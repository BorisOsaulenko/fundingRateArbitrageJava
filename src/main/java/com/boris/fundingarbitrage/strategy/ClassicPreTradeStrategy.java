package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.math.BigDecimal;
import java.math.MathContext;

public class ClassicPreTradeStrategy implements PreTradeStrategy {
	private static final BigDecimal MIN_F_SPREAD = new BigDecimal("0"); // 0%
	private static final BigDecimal CLOSE_F_SPREAD_EPS = new BigDecimal("0.0001"); // 0.01%
	private static final BigDecimal MIN_O_SPREAD = new BigDecimal("0");

	private static BigDecimal perCoinNotional(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		BigDecimal longAsk = longExchange.bookTicker().askPrice();
		BigDecimal shortBid = shortExchange.bookTicker().bidPrice();
		return longAsk.add(shortBid).divide(BigDecimal.TWO, MathContext.DECIMAL64);
	}

	private static BigDecimal oSpread(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		BigDecimal longAsk = longExchange.bookTicker().askPrice();
		BigDecimal shortBid = shortExchange.bookTicker().bidPrice();
		BigDecimal perCoinNotional = perCoinNotional(snapshot);

		return shortBid.subtract(longAsk).divide(perCoinNotional, MathContext.DECIMAL64);
	}

	private static BigDecimal fSpread(ArbitrageSnapshot snapshot) {
		BigDecimal longFundingRate = snapshot.longExchange().fundingRate().rate();
		BigDecimal shortFundingRate = snapshot.shortExchange().fundingRate().rate();
		return shortFundingRate.subtract(longFundingRate);
	}

	private static BigDecimal totalFees(ArbitrageSnapshot snapshot) {
		BigDecimal result = BigDecimal.ZERO;
		result = result.add(snapshot.longExchange().fees().openTaker());
		result = result.add(snapshot.shortExchange().fees().openTaker());
		result = result.add(snapshot.longExchange().fees().closeTaker());
		result = result.add(snapshot.shortExchange().fees().closeTaker());

		return result;
	}

	public int compareSnapshots(ArbitrageSnapshot first, ArbitrageSnapshot second) {
		boolean firstGood = snapshotGoodEnough(first);
		boolean secondGood = snapshotGoodEnough(second);
		if (firstGood && !secondGood) return 1;
		if (!firstGood && secondGood) return -1;

		BigDecimal firstFSpread = fSpread(first);
		BigDecimal secondFSpread = fSpread(second);

		BigDecimal fSpreadDiff = firstFSpread.subtract(secondFSpread).abs();
		if (fSpreadDiff.compareTo(CLOSE_F_SPREAD_EPS) < 0) {
			return oSpread(first).compareTo(oSpread(second));
		}
		return firstFSpread.compareTo(secondFSpread);
	}

	public boolean snapshotGoodEnough(ArbitrageSnapshot snapshot) {
		boolean fSpreadGood = fSpread(snapshot).subtract(MIN_F_SPREAD).compareTo(totalFees(snapshot)) > 0;
		boolean oSpreadGood = oSpread(snapshot).compareTo(MIN_O_SPREAD) >= 0;
		return fSpreadGood && oSpreadGood;
	}
}
