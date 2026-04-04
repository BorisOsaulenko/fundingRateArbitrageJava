package com.boris.fundingarbitrage.strategy.pretradestrategy.single;

import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ClassicSinglePreTradeStrategy implements SinglePreTradeStrategy {
	private static final BigDecimal O_SPREAD_FLOOR = new BigDecimal("0.005"); // 0.5%
	private static final BigDecimal F_SPREAD_FLOOR = new BigDecimal("0.005"); // 0%

	public static BigDecimal oSpread(ExchangeSnapshot sn) {
		BigDecimal spotAsk = sn.spotSnapshot().bookTicker().askPrice();
		BigDecimal futuresBid = sn.futuresSnapshot().bookTicker().bidPrice();
		BigDecimal notional = spotAsk.add(futuresBid).divide(BigDecimal.TWO, RoundingMode.HALF_UP);

		return futuresBid.subtract(spotAsk).divide(notional, 8, RoundingMode.HALF_EVEN);
	}

	public static BigDecimal fSpread(ExchangeSnapshot sn) {
		return sn.futuresSnapshot().fundingRate().rate();
	}

	public static BigDecimal totalFees(ExchangeConstantData cd) {
		BigDecimal result = BigDecimal.ZERO;
		result = result.add(cd.fees(TradeMarket.FUTURES).openTaker());
		result = result.add(cd.fees(TradeMarket.SPOT).openTaker());
		result = result.add(cd.fees(TradeMarket.FUTURES).closeTaker());
		result = result.add(cd.fees(TradeMarket.SPOT).closeTaker());
		return result;
	}

	@Override
	public TradeDirections getDirections(ExchangeData data) {
		return new TradeDirections(TradeMarket.SPOT, TradeMarket.FUTURES);
	}

	@Override
	public boolean goodToEnter(ExchangeData data) {
		BigDecimal oSpread = oSpread(data.snapshot());
		BigDecimal fSpread = fSpread(data.snapshot());
		BigDecimal totalFees = totalFees(data.constantData());
		return oSpread.subtract(totalFees).compareTo(O_SPREAD_FLOOR) > 0 && fSpread.compareTo(F_SPREAD_FLOOR) > 0;
	}

	@Override
	public BigDecimal expectedGain(ExchangeData data) {
		BigDecimal oSpread = oSpread(data.snapshot());
		BigDecimal fSpread = fSpread(data.snapshot());
		BigDecimal fees = totalFees(data.constantData());
		return oSpread.add(fSpread).subtract(fees);
	}

	@Override
	public boolean requiredSpot() {
		return true;
	}

	@Override
	public boolean requiredFutures() {
		return true;
	}
}
