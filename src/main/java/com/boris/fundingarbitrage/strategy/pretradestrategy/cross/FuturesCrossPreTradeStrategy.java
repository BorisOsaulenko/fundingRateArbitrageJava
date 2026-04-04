package com.boris.fundingarbitrage.strategy.pretradestrategy.cross;

import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.FuturesSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public final class FuturesCrossPreTradeStrategy implements CrossPreTradeStrategy {
	private static final BigDecimal F_SPREAD_FLOOR = new BigDecimal("0.005");
	private static final BigDecimal O_SPREAD_FLOOR = new BigDecimal("0.005"); // 0.5%

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
		Instant longSettlement = longSn.fundingRate().settlement();
		Instant shortSettlement = shortSn.fundingRate().settlement();

		BigDecimal longFunding = longSn.fundingRate().rate().multiply(longSn.markPrice().price()).negate();
		BigDecimal shortFunding = shortSn.fundingRate().rate().multiply(shortSn.markPrice().price());

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
		BigDecimal futuresOSpread = futuresOSpread(longData.futuresSnapshot(), shortData.futuresSnapshot());
		BigDecimal futuresFSpread = closestFuturesFSpread(longData.futuresSnapshot(), shortData.futuresSnapshot());
		BigDecimal totalFuturesFees = totalFuturesFees(longData.futuresConstantData(), shortData.futuresConstantData());
		return futuresOSpread.add(futuresFSpread).subtract(totalFuturesFees);
	}

	@Override
	public boolean goodToEnter(ExchangeData longData, ExchangeData shortData) {
		BigDecimal futuresFSpread = closestFuturesFSpread(longData.futuresSnapshot(), shortData.futuresSnapshot());
		BigDecimal futuresOSpread = futuresOSpread(longData.futuresSnapshot(), shortData.futuresSnapshot());
		BigDecimal totalFuturesFees = totalFuturesFees(longData.futuresConstantData(), shortData.futuresConstantData());
		return futuresFSpread.subtract(totalFuturesFees).compareTo(F_SPREAD_FLOOR) > 0 &&
					 futuresOSpread.compareTo(O_SPREAD_FLOOR) > 0;
	}

	@Override
	public TradeDirections getDirections(ExchangeData longData, ExchangeData shortData) {
		return new TradeDirections(TradeMarket.FUTURES, TradeMarket.FUTURES);
	}

	@Override
	public boolean requiredSpot() {
		return false;
	}

	@Override
	public boolean requiredFutures() {
		return true;
	}
}
