package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ClassicPreTradeStrategy implements PreTradeStrategy {
	private static final BigDecimal MIN_GAIN = new BigDecimal("0.03"); // 0.3%
	private static final BigDecimal MIN_OSPREAD = new BigDecimal("-0.005"); // -0.5%
	private static final Duration BEFORE_ENTER = Duration.of(5, ChronoUnit.SECONDS);
	private static final Duration FUNDING_CLOSE = Duration.of(10, ChronoUnit.SECONDS);
	private static final BigDecimal GOOD_OSPREAD = new BigDecimal("0.01"); // 1%

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
		boolean firstGood = goodToEnter(first);
		boolean secondGood = goodToEnter(second);
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
	public boolean goodToEnter(ArbitrageData d) {
		Duration tillEnter = Instant.now().until(d.snapshot().closestSettlement());
		if (tillEnter.compareTo(BEFORE_ENTER) < 0)
			return false; // too late to enter, not enough time to place orders and get filled

		BigDecimal fs = closestFSpread(d.snapshot());
		BigDecimal fees = totalFees(d.constantData());
		BigDecimal os = oSpread(d.snapshot());

		boolean goodOnFunding =
						fs.subtract(fees).compareTo(MIN_GAIN) > 0 &&
						os.compareTo(MIN_OSPREAD) > 0 &&
						tillEnter.compareTo(FUNDING_CLOSE) < 0; // Ensure funding does not change too much before application

		boolean goodOnOSpread =
						os.subtract(fees).compareTo(GOOD_OSPREAD) > 0 &&
						fs.compareTo(BigDecimal.ZERO) >= 0;

		return goodOnFunding || goodOnOSpread;
	}
}
