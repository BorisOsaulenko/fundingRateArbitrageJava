package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.FuturesExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

public final class FuturesPreTradeStrategy implements PreTradeStrategy {
	private static final BigDecimal F_SPREAD_FLOOR = new BigDecimal("0.005");
	private static final BigDecimal O_SPREAD_FLOOR = new BigDecimal("0.005"); // 0.5%
	private static final Duration beforeFunding = Duration.ofSeconds(10);
	private static final BigDecimal NEG_INF = new BigDecimal("-1000");

	public static BigDecimal notional(FuturesSnapshot longSn, FuturesSnapshot shortSn) {
		BigDecimal longAsk = longSn.bookTicker().askPrice();
		BigDecimal shortBid = shortSn.bookTicker().bidPrice();
		return longAsk.add(shortBid).divide(BigDecimal.TWO, RoundingMode.HALF_EVEN);
	}

	public static BigDecimal futuresOSpread(FuturesSnapshot longSn, FuturesSnapshot shortSn) {
		BigDecimal longAsk = longSn.bookTicker().askPrice();
		BigDecimal shortBid = shortSn.bookTicker().bidPrice();
		BigDecimal notional = longAsk.add(shortBid).divide(BigDecimal.TWO, RoundingMode.HALF_EVEN);

		return shortBid.subtract(longAsk).divide(notional, 8, RoundingMode.HALF_EVEN);
	}

	public static BigDecimal closestFuturesFSpread(FuturesSnapshot longSn, FuturesSnapshot shortSn) {
		Instant longSettlement = longSn.funding().settlement();
		Instant shortSettlement = shortSn.funding().settlement();

		BigDecimal longFunding = longSn.funding().rate().multiply(longSn.mark().price()).negate();
		BigDecimal shortFunding = shortSn.funding().rate().multiply(shortSn.mark().price());

		if (longSettlement.equals(shortSettlement))
			return shortFunding.add(longFunding).divide(notional(longSn, shortSn), 8, RoundingMode.HALF_UP);
		if (longSettlement.isAfter(shortSettlement))
			return shortFunding.divide(notional(longSn, shortSn), 8, RoundingMode.HALF_UP);
		return longFunding.divide(notional(longSn, shortSn), 8, RoundingMode.HALF_UP);
	}

	public static BigDecimal totalFuturesFees(FuturesConstantData longCd, FuturesConstantData shortCd) {
		BigDecimal result = BigDecimal.ZERO;
		result = result.add(longCd.fees().openTaker());
		result = result.add(shortCd.fees().openTaker());
		result = result.add(longCd.fees().closeTaker());
		result = result.add(shortCd.fees().closeTaker());

		return result;
	}

	@Override
	public BigDecimal expectedGain(ExchangeData longData, ExchangeData shortData) {
		if (notOnlyFutures(longData, shortData)) return NEG_INF;
		FuturesExchangeData longD = (FuturesExchangeData) longData;
		FuturesExchangeData shortD = (FuturesExchangeData) shortData;

		BigDecimal oSpread = futuresOSpread(longD.snapshot(), shortD.snapshot());
		BigDecimal fSpread = closestFuturesFSpread(longD.snapshot(), shortD.snapshot());
		BigDecimal totalFees = totalFuturesFees(longD.constantData(), shortD.constantData());
		return oSpread.add(fSpread).subtract(totalFees);
	}

	private boolean notOnlyFutures(ExchangeData longData, ExchangeData shortData) {
		return !(longData instanceof FuturesExchangeData) || !(shortData instanceof FuturesExchangeData);
	}

	private Instant fundingTimestamp(FuturesExchangeData longData, FuturesExchangeData shortData) {
		Instant longSettlement = longData.snapshot().funding().settlement();
		Instant shortSettlement = shortData.snapshot().funding().settlement();

		if (longSettlement.isBefore(shortSettlement))
			return longSettlement;
		return shortSettlement;
	}

	@Override
	public boolean goodToEnter(ExchangeData longData, ExchangeData shortData) {
		if (notOnlyFutures(longData, shortData)) return false;
		FuturesExchangeData longD = (FuturesExchangeData) longData;
		FuturesExchangeData shortD = (FuturesExchangeData) shortData;

		Instant now = Instant.now();
		Instant fundingTimestamp = fundingTimestamp(longD, shortD);
		if (now.isBefore(fundingTimestamp.minus(beforeFunding))) return false;

		BigDecimal futuresFSpread = closestFuturesFSpread(longD.snapshot(), shortD.snapshot());
		BigDecimal futuresOSpread = futuresOSpread(longD.snapshot(), shortD.snapshot());
		BigDecimal totalFuturesFees = totalFuturesFees(longD.constantData(), shortD.constantData());
		return futuresFSpread.subtract(totalFuturesFees).compareTo(F_SPREAD_FLOOR) > 0 &&
					 futuresOSpread.compareTo(O_SPREAD_FLOOR) > 0;
	}

	@Override
	public TradeDirections getDirections(ExchangeData longData, ExchangeData shortData) {
		return new TradeDirections(TradeMarket.FUTURES, TradeMarket.FUTURES);
	}
}
