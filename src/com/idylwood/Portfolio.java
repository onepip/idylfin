package com.idylwood;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import au.com.bytecode.opencsv.CSVReader;

import com.idylwood.misc.PowerShares;
import com.idylwood.utils.FinUtils;
import com.idylwood.utils.MathUtils;
import com.idylwood.yahoo.Date;
import com.idylwood.yahoo.HistTable;
import com.idylwood.yahoo.HistRow;
import com.idylwood.yahoo.YahooFinance;

public class Portfolio {
	class Item
	{
		final String ticker;
		final double weight;
		public Item(final String ticker, final double weight)
		{
			this.ticker = ticker; this.weight = weight;
		}
	}
	public final List<Item> items = new ArrayList<Item>();
	private final YahooFinance yf = YahooFinance.getInstance();
	private String[] tickers()
	{
		final String []ret = new String[items.size()];
		int i = 0;
		for (Item item : items) ret[i++] = item.ticker;
		return ret;
	}
	public double marketCap()
		throws IOException
	{
		yf.Quotes(tickers()); // force it to batch download them. yay side effects!
		double ret = 0;
		for (final Item it : items)
			ret += it.weight * yf.getQuote(it.ticker).market_cap;
		return ret;
	}
	public double earnings()
		throws IOException
	{
		yf.Quotes(tickers());
		double ret = 0;
		for (final Item it : items)
			ret += it.weight * yf.getQuote(it.ticker).earnings();
		return ret;
	}
	public double revenue()
		throws IOException
	{
		yf.Quotes(tickers());
		double ret = 0;
		for (final Item it : items)
			ret += it.weight * yf.getQuote(it.ticker).revenue;
		return ret;
	}
	HistTable[] tables()
		throws IOException
	{
		final HistTable[] tables = new HistTable[items.size()];
		for (int i = 0 ; i < items.size(); i++)
			tables[i] = yf.HistoricalPrices(items.get(i).ticker);
		return tables;
	}
	public double totalReturn()
		throws IOException
	{
		final HistTable[] tables = FinUtils.merge(tables());
		for (int i = 0; i < tables.length; i++)
			tables[i] = tables[i]
				.AdjustOHLCWithReinvestment();
		return totalReturn(tables);
	}
	public double totalReturn(final Date begin, final Date end)
		throws IOException
	{
		final HistTable[] tables = FinUtils.merge(tables());
		// TODO check that the begin, end is actually valid.
		for (int i = 0; i < tables.length; i++)
			tables[i] = tables[i]
				.SubTable(begin,end)
				.AdjustOHLCWithReinvestment();
		return totalReturn(tables);
	}
	private static Portfolio reassignWeights(final Portfolio arg, final double[] weights)
	{
		final Portfolio ret = new Portfolio();
		int i = 0;
		for (final Item it : items)
			ret.add(new Item(it.ticker, weights[i++]));
		return ret;
	}
	public Portfolio markowitzOptimize(final Date begin, final Date end)
		throws IOException
	{
		final HistTable data[] = FinUtils.merge(tables());
		for (int i = 0; i < data.length; i++)
			data[i] = data[i].SubTable(begin,end).AdjustOHLCWithReinvestment();
		final double portfolio_return = Math.abs(totalLogReturn(data));
		return reassignWeights(this, MarkowitzPortfolio(data, portfolio_return));
	}
	// start of backtesting interface
	private void doBacktest()
		throws IOException
	{
		final HistTable data[] = FinUtils.merge(tables());
	}
	private double totalLogReturn(final HistTable[] tables)
		throws IOException
	{
		// assume dividends reinvestment strategy is just
		// each symbol that pays a dividend is reinvested
		// in that company.
		final double returns[] = new double[items.size()];
		final double weights[] = new double[items.size()];
		// fwahaha abusing the final keyword
		for (int i = 0; i < items.size(); i++)
		{
			final Item it = items.get(i);
			final HistTable table = tables[i];
			//ret += FinUtils.totalReturn(table.CloseArray()) * it.weight;
			//sanity_check += it.weight;
			returns[i] = FinUtils.totalReturn(table.CloseArray());
			weights[i] = it.weight;
		}
		final double epsilon = 1e-3;
		System.out.println(MathUtils.sumSlow(weights));
		return MathUtils.linearCombinationSlow(returns,weights);
	}
	public static void main(String[]args)
		throws IOException, FileNotFoundException
	{
		final Reader reader = //new StringReader(PowerShares.getFundHoldings("QQQ"));
		//new FileReader("/media/files/Downloads/QQQHoldings.csv");
			new FileReader("/media/files/Downloads/DIA_All_Holdings.csv");
		final CSVReader csv = new CSVReader(reader);
		final List<String[]> allLines = csv.readAll();
		csv.close();
		allLines.remove(0);
		final Portfolio p = new Portfolio();
		for (final String[] line : allLines)
			//p.items.add(p.new Item(line[2], Double.parseDouble(line[4]) / 100));
			p.items.add(p.new Item(line[1], Double.parseDouble(line[2])/100));
		final HistTable table = FinUtils.merge(p.tables())[0];
		final Date end = table.data.get(table.data.size()-1).date;
		final Date begin = end.subtractYears(1);
		final HistTable foo = table.SubTable(begin,end);
		for (final HistRow row : foo.data)
			System.out.println(row);
		System.out.println(p.totalReturn(begin,end));
		/*
		System.out.println("Market Cap: "+p.marketCap());
		System.out.println("Earnings: "+p.earnings());
		System.out.println("Revenue: "+p.revenue());
		*/
	}
}
