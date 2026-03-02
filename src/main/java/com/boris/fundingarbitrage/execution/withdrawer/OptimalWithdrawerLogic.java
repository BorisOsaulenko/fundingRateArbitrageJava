package com.boris.fundingarbitrage.execution.withdrawer;

import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptimalWithdrawerLogic {
	private final Map<ExchangeName, BigDecimal> freeByExchanges = new HashMap<>();
	private final Map<ExchangeName, RequiredBalances> requiredBalancesByExchanges = new HashMap<>();
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
		if (Long.toBinaryString(processedBase4State).equals("11010100")) {
			Logger.log(cumFee + " " + minL + " " + minS + " " + freeL + " " + freeS + " " + freeD);
		}

		if (cumFee.compareTo(minFee) >= 0 ||
				minS.compareTo(params.topUpShort()) > 0 ||
				minL.compareTo(params.topUpLong()) > 0) {
			return;
		}

		if (processed < params.withdrawExchanges().size()) {
			InputItem item = params.withdrawExchanges().get(processed);
			processBase4Encoding(processed + 1, 4 * processedBase4State, cumFee, minL, minS, freeL, freeS, freeD); // state 0

			RequiredBalances rb = requiredBalancesByExchanges.get(item.exName());
			if (rb == null) {
				rb = new RequiredBalances(item);
				requiredBalancesByExchanges.put(item.exName(), rb);
			}

			if (item.shortFee() != null && item.balance().compareTo(rb.requiredShortOnly()) >= 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 1,
								cumFee.add(item.shortFee()),
								minL,
								minS.add(rb.requiredShortOnly()),
								freeL,
								freeS.add(item.balance()).subtract(rb.requiredShortOnly()),
								freeD
				); // state 1 - only short
			}

			if (item.shortFee() != null && item.longFee() != null && item.balance().compareTo(rb.requiredDiagonal()) >= 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 2,
								cumFee.add(item.shortFee()).add(item.longFee()),
								minL.add(rb.requiredLongOnly()),
								minS.add(rb.requiredShortOnly()),
								freeL,
								freeS,
								freeD.add(item.balance().subtract(rb.requiredDiagonal()))
				);
			} // state 2 - both / diagonal

			if (item.longFee() != null && item.balance().compareTo(rb.requiredLongOnly()) >= 0) {
				processBase4Encoding(
								processed + 1,
								4 * processedBase4State + 3,
								cumFee.add(item.longFee()),
								minL.add(rb.requiredLongOnly()),
								minS,
								freeL.add(item.balance()).subtract(rb.requiredLongOnly()),
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
			longWd.exName = shortWd.exName = item.exName();
			longWd.wdPrecisionPoints = item.longWdPrecision();
			shortWd.wdPrecisionPoints = item.shortWdPrecision();

			RequiredBalances rb = requiredBalancesByExchanges.get(item.exName());
			BigDecimal free = item.balance();

			if (status == 1 || status == 2) {
				shortWd.amount = rb.requiredShortOnly();
				shortLeft = shortLeft.subtract(shortWd.amount);
				shortWd.fee = item.shortFee();
				shortWd.toLong = false;
				withdrawEntries.add(shortWd);
				free = free.subtract(rb.requiredShortOnly());
			}
			if (status == 2 || status == 3) {
				longWd.amount = rb.requiredLongOnly();
				longLeft = longLeft.subtract(longWd.amount);
				longWd.fee = item.longFee();
				longWd.toLong = true;
				withdrawEntries.add(longWd);
				free = free.subtract(rb.requiredLongOnly());
			}

			freeByExchanges.put(longWd.exName, free);

			exchangeIdx--;
			optimalCopy /= 4;
		}
	}

	private void fulfillWithdrawEntries() {
		for (WithdrawEntry wd : withdrawEntries) {
			BigDecimal free = freeByExchanges.get(wd.exName).setScale(wd.wdPrecisionPoints, RoundingMode.DOWN);
			BigDecimal shortLeftRounded = shortLeft.setScale(wd.wdPrecisionPoints, RoundingMode.UP);
			BigDecimal longLeftRounded = longLeft.setScale(wd.wdPrecisionPoints, RoundingMode.UP);

			if (wd.status == 1) {
				BigDecimal additional = free.min(shortLeftRounded);
				wd.amount = wd.amount.add(additional);
				shortLeft = shortLeft.subtract(additional).max(BigDecimal.ZERO);
			} else if (wd.status == 3) {
				BigDecimal additional = free.min(longLeftRounded);
				wd.amount = wd.amount.add(additional);
				longLeft = longLeft.subtract(additional).max(BigDecimal.ZERO);
			}
		}

		for (WithdrawEntry wd : withdrawEntries) {
			BigDecimal free = freeByExchanges.get(wd.exName).setScale(wd.wdPrecisionPoints, RoundingMode.DOWN);
			BigDecimal shortLeftRounded = shortLeft.setScale(wd.wdPrecisionPoints, RoundingMode.UP);
			BigDecimal longLeftRounded = longLeft.setScale(wd.wdPrecisionPoints, RoundingMode.UP);

			if (wd.status == 2) {
				if (wd.toLong) {
					BigDecimal additional = free.min(longLeftRounded);
					wd.amount = wd.amount.add(additional);
					longLeft = longLeft.subtract(additional);
					freeByExchanges.put(wd.exName, free.subtract(additional).max(BigDecimal.ZERO));
				} else {
					BigDecimal additional = free.min(shortLeftRounded);
					wd.amount = wd.amount.add(additional);
					shortLeft = shortLeft.subtract(additional);
					freeByExchanges.put(wd.exName, free.subtract(additional).max(BigDecimal.ZERO));
				}
			}
		}
	}

	private List<OutputItem> formatOutput() {
		List<OutputItem> result = new ArrayList<>();
		for (WithdrawEntry wd : withdrawEntries) {
			result.add(new OutputItem(wd.exName, wd.amount, wd.fee, wd.toLong));
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
		fulfillWithdrawEntries();
		return formatOutput();
	}

	private record RequiredBalances(
					BigDecimal requiredLongOnly, BigDecimal requiredShortOnly, BigDecimal requiredDiagonal
	) {
		private RequiredBalances(InputItem item) {
			BigDecimal minLongWdOrZero = item.minLongWd() == null ? BigDecimal.ZERO : item.minLongWd();
			BigDecimal minShortWdOrZero = item.minShortWd() == null ? BigDecimal.ZERO : item.minShortWd();

			BigDecimal shortFeeOrZero = item.shortFee() == null ? BigDecimal.ZERO : item.shortFee();
			BigDecimal longFeeOrZero = item.longFee() == null ? BigDecimal.ZERO : item.longFee();

			BigDecimal requiredBalanceForLongOnly = minLongWdOrZero.max(longFeeOrZero)
																														 .setScale(item.longWdPrecision(), RoundingMode.UP);
			BigDecimal requiredBalanceForShortOnly = minShortWdOrZero.max(shortFeeOrZero)
																															 .setScale(item.shortWdPrecision(), RoundingMode.UP);
			BigDecimal requiredBalanceForDiagonal = requiredBalanceForLongOnly.add(requiredBalanceForShortOnly);

			this(requiredBalanceForLongOnly, requiredBalanceForShortOnly, requiredBalanceForDiagonal);
		}
	}

	public record InputItem(
					ExchangeName exName,
					BigDecimal balance,
					BigDecimal longFee,
					BigDecimal shortFee,
					BigDecimal minLongWd,
					BigDecimal minShortWd,
					int longWdPrecision,
					int shortWdPrecision
	) {
	}

	public record InputParams(BigDecimal topUpLong, BigDecimal topUpShort, List<InputItem> withdrawExchanges) {
	}

	public record OutputItem(ExchangeName exName, BigDecimal amount, BigDecimal fee, boolean toLong) {
	}

	private static class WithdrawEntry {
		int wdPrecisionPoints;
		long status;
		ExchangeName exName;
		BigDecimal amount;
		BigDecimal fee;
		boolean toLong;

		WithdrawEntry() {
			amount = BigDecimal.ZERO;
			fee = BigDecimal.ZERO;
		}

		@Override
		public String toString() {
			return "WithdrawEntry{" +
						 "status=" +
						 status +
						 " exName=" +
						 exName +
						 ", amount=" +
						 amount +
						 ", fee=" +
						 fee +
						 ", toLong=" +
						 toLong +
						 "}\n";
		}
	}
}
