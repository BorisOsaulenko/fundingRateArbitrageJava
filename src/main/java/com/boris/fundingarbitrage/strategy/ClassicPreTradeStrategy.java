package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class ClassicPreTradeStrategy implements PreTradeStrategy {
	private static final BigDecimal MIN_GAIN = new BigDecimal("0.03"); // 0.3%

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

	public static BigDecimal totalFees(ArbitrageConstantData data) {
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
		if (fSpreadDiff.compareTo(BigDecimal.ZERO) == 0) // rare but happens
			return oSpread(first.snapshot()).compareTo(oSpread(second.snapshot()));

		return firstFSpread.compareTo(secondFSpread);
	}

	@Override
	public boolean arbDataGoodEnough(ArbitrageData d) {
		BigDecimal fs = closestFSpread(d.snapshot());
		BigDecimal fees = totalFees(d.constantData());
		BigDecimal os = oSpread(d.snapshot());

		if (fs.compareTo(fees) < 0) return false;
		return fs.add(os).subtract(fees).compareTo(MIN_GAIN) > 0;
	}
}
