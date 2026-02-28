package com.boris.fundingarbitrage.execution.withdrawer;

import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.math.BigDecimal;
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

		if (processed < params.availableToWd().size()) {
			InputItem item = params.availableToWd().get(processed);
			processBase4Encoding(processed + 1, 4 * processedBase4State, cumFee, minL, minS, freeL, freeS, freeD); // state 0

			BigDecimal balanceRequiredForShort = item.minShortWd().add(item.shortFee());
			BigDecimal balanceRequiredForLong = item.minLongWd().add(item.longFee());

			if (item.shortFee() != null && item.balance().compareTo(balanceRequiredForShort) >= 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 1,
								cumFee.add(item.shortFee()),
								minL,
								minS.add(item.minShortWd()),
								freeL,
								freeS.add(item.balance()).subtract(balanceRequiredForShort),
								freeD
				); // state 1 - only short
			}

			if (item.shortFee() != null &&
					item.longFee() != null &&
					item.balance().compareTo(balanceRequiredForLong.add(balanceRequiredForShort)) >= 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 2,
								cumFee.add(item.shortFee()).add(item.longFee()),
								minL.add(item.minLongWd()),
								minS.add(item.minLongWd()),
								freeL,
								freeS,
								freeD.add(item.balance()).subtract(balanceRequiredForLong).subtract(balanceRequiredForShort)
				);
			} // state 2 - both / diagonal

			if (item.longFee() != null && item.balance().compareTo(balanceRequiredForLong) > 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 3,
								cumFee.add(item.longFee()),
								minL.add(item.minLongWd()),
								minS,
								freeL.add(item.balance()).subtract(balanceRequiredForLong),
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
				shortLeft = shortLeft.subtract(shortWd.amount);
				shortWd.fee = params.availableToWd().get(exchangeIdx).shortFee();
				shortWd.toLong = false;
				withdrawEntries.add(shortWd);
			}
			if (status == 2 || status == 3) {
				longWd.amount = params.availableToWd().get(exchangeIdx).minLongWd();
				longLeft = longLeft.subtract(longWd.amount);
				longWd.fee = params.availableToWd().get(exchangeIdx).longFee();
				longWd.toLong = true;
				withdrawEntries.add(longWd);
			}

			freeByExchanges.put(
							longWd.ex,
							item.balance().subtract(longWd.amount).subtract(shortWd.amount).subtract(longWd.fee).subtract(shortWd.fee)
			);

			exchangeIdx--;
			optimalCopy /= 4;
		}
	}

	private void fullfillWithdrawEntries() {
		for (WithdrawEntry wd : withdrawEntries) {
			BigDecimal free = freeByExchanges.get(wd.ex);
			if (wd.status == 1) {
				BigDecimal additional = free.min(shortLeft);
				wd.amount = wd.amount.add(additional);
				shortLeft = shortLeft.subtract(additional);
			} else if (wd.status == 3) {
				BigDecimal additional = free.min(longLeft);
				wd.amount = wd.amount.add(additional);
				longLeft = longLeft.subtract(additional);
			}
		}

		for (WithdrawEntry wd : withdrawEntries) {
			BigDecimal free = freeByExchanges.get(wd.ex);
			if (wd.status == 2) {
				if (wd.toLong) {
					BigDecimal additional = free.min(longLeft);
					wd.amount = wd.amount.add(additional);
					longLeft = longLeft.subtract(additional);
					freeByExchanges.put(wd.ex, free.subtract(additional));
				} else {
					BigDecimal additional = free.min(shortLeft);
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
					BigDecimal minShortWd
	) {
	}

	public record InputParams(BigDecimal topUpLong, BigDecimal topUpShort, List<InputItem> availableToWd) {
	}

	public record OutputItem(BaseExchange ex, BigDecimal amount, BigDecimal fee, boolean toLong) {
	}

	private static class WithdrawEntry {
		long status;
		BaseExchange ex;
		BigDecimal amount;
		BigDecimal fee;
		boolean toLong;

		@Override
		public String toString() {
			return "WithdrawEntry{" + "ex=" + ex.name + ", amount=" + amount + ", fee=" + fee + ", toLong=" + toLong + "}\n";
		}
	}
}
