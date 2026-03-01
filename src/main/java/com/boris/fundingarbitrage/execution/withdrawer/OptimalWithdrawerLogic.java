package com.boris.fundingarbitrage.execution.withdrawer;

import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptimalWithdrawerLogic {
	private final Map<BaseExchange, BigDecimal> freeByExchanges = new HashMap<>();
	private final List<WithdrawEntry> withdrawEntries = new ArrayList<>();
	private BigDecimal minFee = BigDecimal.valueOf(100000);
	private InputParams params;
	private long optimalBase4 = 0;
	private BigDecimal longLeft;
	private BigDecimal shortLeft;

	private void processBase4Encoding(
					int processed,
					long processedBase4State,
					BigDecimal cumFee,
					BigDecimal minL,
					BigDecimal minS,
					BigDecimal freeL,
					BigDecimal freeS,
					BigDecimal freeD
	) {
		if (cumFee.compareTo(minFee) >= 0 ||
				minS.compareTo(params.topUpShort()) > 0 ||
				minL.compareTo(params.topUpLong()) > 0) {
			return;
		}

		if (processed < params.withdrawExchanges().size()) {
			InputItem item = params.withdrawExchanges().get(processed);
			processBase4Encoding(processed + 1, 4 * processedBase4State, cumFee, minL, minS, freeL, freeS, freeD); // state 0

			BigDecimal takenFromFreeS = BigDecimal.ZERO;
			if (item.shortFee() != null)
				takenFromFreeS = item.minShortWd().add(item.shortFee()).setScale(item.wdPrecisionPoints(), RoundingMode.UP);

			BigDecimal takenFromFreeL = BigDecimal.ZERO;
			if (item.longFee() != null)
				takenFromFreeL = item.minLongWd().add(item.longFee()).setScale(item.wdPrecisionPoints(), RoundingMode.UP);

			if (item.shortFee() != null && item.balance().compareTo(item.minShortWd()) >= 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 1,
								cumFee.add(item.shortFee()),
								minL,
								minS.add(item.minShortWd()),
								freeL,
								freeS.add(item.balance()).subtract(takenFromFreeL),
								freeD
				); // state 1 - only short
			}

			if (item.shortFee() != null &&
					item.longFee() != null &&
					item.balance().compareTo(item.minShortWd().add(item.minLongWd())) >= 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 2,
								cumFee.add(item.shortFee()).add(item.longFee()),
								minL.add(item.minLongWd()),
								minS.add(item.minLongWd()),
								freeL,
								freeS,
								freeD.add(item.balance().subtract(takenFromFreeS).subtract(takenFromFreeL))
				);
			} // state 2 - both / diagonal

			if (item.longFee() != null && item.balance().compareTo(item.minLongWd()) > 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 3,
								cumFee.add(item.longFee()),
								minL.add(item.minLongWd()),
								minS,
								freeL.add(item.balance()).subtract(takenFromFreeL),
								freeS,
								freeD
				);
			} // state 3 - only long

			return;
		}

		// here the processed = items.size()
		BigDecimal totalLong = minL.add(freeL).min(params.topUpLong());
		BigDecimal totalShort = minS.add(freeS).min(params.topUpShort());

		BigDecimal leftToWdShort = params.topUpShort().subtract(totalShort);
		BigDecimal leftToWdLong = params.topUpLong().subtract(totalLong);
		if (leftToWdLong.add(leftToWdShort).compareTo(freeD) > 0) return;

		optimalBase4 = processedBase4State;
		minFee = cumFee;
	}

	private void parseWithdrawEntries() {
		long optimalCopy = optimalBase4;
		int exchangeIdx = params.withdrawExchanges().size() - 1;
		longLeft = params.topUpLong();
		shortLeft = params.topUpShort();

		while (optimalCopy > 0) {
			long status = optimalCopy % 4;
			if (status == 0) {
				exchangeIdx--;
				optimalCopy /= 4;
				continue;
			}

			InputItem item = params.withdrawExchanges().get(exchangeIdx);
			WithdrawEntry longWd = new WithdrawEntry();
			WithdrawEntry shortWd = new WithdrawEntry();

			longWd.status = shortWd.status = status;
			longWd.ex = shortWd.ex = item.ex();
			longWd.wdPrecisionPoints = shortWd.wdPrecisionPoints = item.wdPrecisionPoints();

			if (status == 1 || status == 2) {
				shortWd.amount = item.minShortWd();
				shortLeft = shortLeft.subtract(shortWd.amount);
				shortWd.fee = item.shortFee();
				shortWd.toLong = false;
				withdrawEntries.add(shortWd);
			}
			if (status == 2 || status == 3) {
				longWd.amount = item.minLongWd();
				longLeft = longLeft.subtract(longWd.amount);
				longWd.fee = item.longFee();
				longWd.toLong = true;
				withdrawEntries.add(longWd);
			}

			freeByExchanges.put(
							longWd.ex,
							item.balance()
											.subtract(longWd.amount)
											.subtract(shortWd.amount)
											.subtract(longWd.fee)
											.subtract(shortWd.fee)
											.setScale(longWd.wdPrecisionPoints, RoundingMode.DOWN)
			);

			exchangeIdx--;
			optimalCopy /= 4;
		}
	}

	private void fullfillWithdrawEntries() {
		for (WithdrawEntry wd : withdrawEntries) {
			BigDecimal free = freeByExchanges.get(wd.ex);
			if (wd.status == 1) {
				BigDecimal additional = free.min(shortLeft).setScale(wd.wdPrecisionPoints, RoundingMode.DOWN);
				wd.amount = wd.amount.add(additional);
				shortLeft = shortLeft.subtract(additional);
			} else if (wd.status == 3) {
				BigDecimal additional = free.min(longLeft).setScale(wd.wdPrecisionPoints, RoundingMode.DOWN);
				wd.amount = wd.amount.add(additional);
				longLeft = longLeft.subtract(additional);
			}
		}

		for (WithdrawEntry wd : withdrawEntries) {
			BigDecimal free = freeByExchanges.get(wd.ex);
			if (wd.status == 2) {
				if (wd.toLong) {
					BigDecimal additional = free.min(longLeft).setScale(wd.wdPrecisionPoints, RoundingMode.DOWN);
					wd.amount = wd.amount.add(additional);
					longLeft = longLeft.subtract(additional);
					freeByExchanges.put(wd.ex, free.subtract(additional));
				} else {
					BigDecimal additional = free.min(shortLeft).setScale(wd.wdPrecisionPoints, RoundingMode.DOWN);
					wd.amount = wd.amount.add(additional);
					shortLeft = shortLeft.subtract(additional);
					freeByExchanges.put(wd.ex, free.subtract(additional));
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
		processBase4Encoding(
						0,
						0,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.ZERO
		);
		if (optimalBase4 == 0) {
			return null;
		}

		parseWithdrawEntries();
		fullfillWithdrawEntries();
		return formatOutput();
	}

	public record InputItem(
					BaseExchange ex,
					BigDecimal balance,
					BigDecimal longFee,
					BigDecimal shortFee,
					BigDecimal minLongWd,
					BigDecimal minShortWd,
					int wdPrecisionPoints
	) {
		public InputItem(
						BaseExchange ex,
						BigDecimal balance,
						BigDecimal longFee,
						BigDecimal shortFee,
						BigDecimal minLongWd,
						BigDecimal minShortWd,
						int wdPrecisionPoints
		) {
			assert wdPrecisionPoints > 0;
			this.ex = ex;
			this.balance = balance.setScale(wdPrecisionPoints, RoundingMode.DOWN);
			this.longFee = longFee;
			this.shortFee = shortFee;
			this.minLongWd = minLongWd.setScale(wdPrecisionPoints, RoundingMode.UP);
			this.minShortWd = minShortWd.setScale(wdPrecisionPoints, RoundingMode.UP);
			this.wdPrecisionPoints = wdPrecisionPoints;
		}
	}

	public record InputParams(BigDecimal topUpLong, BigDecimal topUpShort, List<InputItem> withdrawExchanges) {
	}

	public record OutputItem(BaseExchange ex, BigDecimal amount, BigDecimal fee, boolean toLong) {
	}

	private static class WithdrawEntry {
		int wdPrecisionPoints;
		long status;
		BaseExchange ex;
		BigDecimal amount;
		BigDecimal fee;
		boolean toLong;

		WithdrawEntry() {
			amount = BigDecimal.ZERO;
			fee = BigDecimal.ZERO;
		}

		@Override
		public String toString() {
			return "WithdrawEntry{" + "ex=" + ex.name + ", amount=" + amount + ", fee=" + fee + ", toLong=" + toLong + "}\n";
		}
	}
}
