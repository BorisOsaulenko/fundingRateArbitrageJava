package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptimalWithdrawerLogic {
	private final Map<BaseExchange, Double> freeByExchanges = new HashMap<>();
	private final List<WithdrawEntry> withdrawEntries = new ArrayList<>();
	private double minFee = 100000;
	private InputParams params;
	private long optimalBase4 = 0;
	private double longLeft;
	private double shortLeft;

	private void processBase4Encoding(
					int processed,
					long processedBase4State,
					double cumFee,
					double minL,
					double minS,
					double freeL,
					double freeS,
					double freeD
	) {
		if (cumFee >= minFee || minS >= params.topUpShort() || minL >= params.topUpLong()) {
			return;
		}

		if (processed < params.availableToWd().size()) {
			InputItem item = params.availableToWd().get(processed);
			processBase4Encoding(processed + 1, 4 * processedBase4State, cumFee, minL, minS, freeL, freeS, freeD); // state 0

			if (item.balance() > item.minShortWd() + item.shortFee()) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 1,
								cumFee + item.shortFee(),
								minL,
								minS + item.minShortWd(),
								freeL,
								freeS + item.balance() - item.minShortWd() - item.shortFee(),
								freeD
				); // state 1 - only short
			}

			if (item.balance() > item.minShortWd() + item.shortFee() + item.minLongWd() + item.longFee()) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 2,
								cumFee + item.shortFee() + item.longFee(),
								minL + item.minLongWd(),
								minS + item.minLongWd(),
								freeL,
								freeS,
								freeD + item.balance() - item.minLongWd() - item.longFee() - item.minShortWd() - item.shortFee()
				);
			} // state 2 - both / diagonal

			if (item.balance() > item.minLongWd() + item.longFee()) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 3,
								cumFee + item.longFee(),
								minL + item.minLongWd(),
								minS,
								freeL + item.balance() - item.minLongWd() - item.longFee(),
								freeS,
								freeD
				);
			} // state 3 - only long

			return;
		}

		// here the processed = items.size()
		double totalLong = Math.min(minL + freeL, params.topUpLong());
		double totalShort = Math.min(minS + freeS, params.topUpShort());

		if (params.topUpShort() - totalShort + params.topUpLong() - totalLong > freeD) {
			return;
		}

		optimalBase4 = processedBase4State;
		minFee = cumFee;
	}

	private void parseWithdrawEntries() {
		long optimalCopy = optimalBase4;
		int exchangeIdx = params.availableToWd().size() - 1;
		longLeft = params.topUpLong();
		shortLeft = params.topUpShort();

		while (optimalCopy > 0) {
			long status = optimalCopy % 4;
			if (status == 0) {
				exchangeIdx--;
				optimalCopy /= 4;
				continue;
			}

			InputItem item = params.availableToWd().get(exchangeIdx);
			WithdrawEntry longWd = new WithdrawEntry();
			WithdrawEntry shortWd = new WithdrawEntry();

			longWd.status = shortWd.status = status;
			longWd.ex = shortWd.ex = item.ex();

			if (status == 1 || status == 2) {
				shortWd.amount = params.availableToWd().get(exchangeIdx).minShortWd();
				shortLeft -= shortWd.amount;
				shortWd.fee = params.availableToWd().get(exchangeIdx).shortFee();
				shortWd.toLong = false;
				withdrawEntries.add(shortWd);
			}
			if (status == 2 || status == 3) {
				longWd.amount = params.availableToWd().get(exchangeIdx).minLongWd();
				longLeft -= longWd.amount;
				longWd.fee = params.availableToWd().get(exchangeIdx).longFee();
				longWd.toLong = true;
				withdrawEntries.add(longWd);
			}

			freeByExchanges.put(longWd.ex, item.balance() - longWd.amount - shortWd.amount - longWd.fee - shortWd.fee);

			exchangeIdx--;
			optimalCopy /= 4;
		}
	}

	private void fullfillWithdrawEntries() {
		for (WithdrawEntry wd : withdrawEntries) {
			double free = freeByExchanges.get(wd.ex);
			if (wd.status == 1) {
				double additional = Math.min(free, shortLeft);
				wd.amount += additional;
				shortLeft -= additional;
			} else if (wd.status == 3) {
				double additional = Math.min(free, longLeft);
				wd.amount += additional;
				longLeft -= additional;
			}
		}

		for (WithdrawEntry wd : withdrawEntries) {
			double free = freeByExchanges.get(wd.ex);
			if (wd.status == 2) {
				if (wd.toLong) {
					double additional = Math.min(free, longLeft);
					wd.amount += additional;
					longLeft -= additional;
					freeByExchanges.put(wd.ex, free - additional);
				} else {
					double additional = Math.min(free, shortLeft);
					wd.amount += additional;
					shortLeft -= additional;
					freeByExchanges.put(wd.ex, free - additional);
				}
			}
		}
	}

	private List<OutputItem> formatOutput() {
		List<OutputItem> result = new ArrayList<>();
		for (WithdrawEntry wd : withdrawEntries) {
			result.add(new OutputItem(wd.ex, wd.amount, wd.fee, wd.toLong));
		}
		return result;
	}

	public List<OutputItem> getOptimalWdPath(InputParams params) {
		this.params = params;
		processBase4Encoding(0, 0, 0, 0, 0, 0, 0, 0);
		if (optimalBase4 == 0) {
			return null;
		}

		parseWithdrawEntries();
		fullfillWithdrawEntries();
		return formatOutput();
	}

	public record InputItem(
					BaseExchange ex, double balance, double longFee, double shortFee, double minLongWd, double minShortWd
	) {
	}

	public record InputParams(double topUpLong, double topUpShort, List<InputItem> availableToWd) {
	}

	public record OutputItem(BaseExchange ex, double amount, double fee, boolean toLong) {
	}

	private static class WithdrawEntry {
		long status;
		BaseExchange ex;
		double amount;
		double fee;
		boolean toLong;

		@Override
		public String toString() {
			return "WithdrawEntry{" + "ex=" + ex.name + ", amount=" + amount + ", fee=" + fee + ", toLong=" + toLong + "}\n";
		}
	}
}
