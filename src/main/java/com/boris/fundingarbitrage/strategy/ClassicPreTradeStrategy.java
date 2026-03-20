package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class ClassicPreTradeStrategy implements PreTradeStrategy {
	private static final BigDecimal SLIPPAGE_TOLERANCE = new BigDecimal("0.002"); // 0.2%
	private static final BigDecimal CLOSE_F_SPREAD_EPS = new BigDecimal("0.0001"); // 0.01%
	private static final BigDecimal MIN_O_SPREAD = new BigDecimal("0.002"); // 0.2%

	public static BigDecimal oSpread(ArbitrageSnapshot snapshot) {
		ExchangeSnapshot longExchange = snapshot.longExchange();
		ExchangeSnapshot shortExchange = snapshot.shortExchange();
		BigDecimal longAsk = longExchange.bookTicker().askPrice();
		BigDecimal shortBid = shortExchange.bookTicker().bidPrice();
		BigDecimal perCoinNotional = snapshot.notional();

		return shortBid.subtract(longAsk).divide(perCoinNotional, 8, RoundingMode.HALF_EVEN);
	}

	public static BigDecimal closestFSpread(ArbitrageSnapshot snapshot) {
		BigDecimal longFundingRate = snapshot.longExchange().fundingRate().rate();
		Instant longFundingTime = snapshot.longExchange().fundingRate().settlement();

		BigDecimal shortFundingRate = snapshot.shortExchange().fundingRate().rate();
		Instant shortFundingTime = snapshot.shortExchange().fundingRate().settlement();

		if (longFundingTime.isBefore(shortFundingTime)) return longFundingRate.negate();
		else if (longFundingTime.equals(shortFundingTime)) return shortFundingRate.subtract(longFundingRate);
		else return shortFundingRate;
	}

	private static BigDecimal totalFees(ArbitrageConstantData data) {
		BigDecimal result = BigDecimal.ZERO;
		result = result.add(data.longData().fees().openTaker());
		result = result.add(data.shortData().fees().openTaker());
		result = result.add(data.longData().fees().closeTaker());
		result = result.add(data.shortData().fees().closeTaker());

		return result;
	}

	@Override
	public int compareArbData(
					ArbitrageData first,
					ArbitrageData second
	) {
		boolean firstGood = arbDataGoodEnough(first);
		boolean secondGood = arbDataGoodEnough(second);
		if (firstGood && !secondGood) return 1;
		if (!firstGood && secondGood) return -1;

		BigDecimal firstFSpread = closestFSpread(first.snapshot());
		BigDecimal secondFSpread = closestFSpread(second.snapshot());

		BigDecimal fSpreadDiff = firstFSpread.subtract(secondFSpread).abs();
		if (fSpreadDiff.compareTo(CLOSE_F_SPREAD_EPS) < 0) {
			return oSpread(first.snapshot()).compareTo(oSpread(second.snapshot()));
		}
		return firstFSpread.compareTo(secondFSpread);
	}

	@Override
	public boolean arbDataGoodEnough(ArbitrageData d) {
		BigDecimal fs = closestFSpread(d.snapshot());
		BigDecimal fees = totalFees(d.constantData());
		boolean fSpread = fs.subtract(SLIPPAGE_TOLERANCE).compareTo(fees) > 0;
		boolean oSpread = oSpread(d.snapshot()).compareTo(MIN_O_SPREAD) >= 0;
		return fSpread && oSpread;
	}
}
