package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ClassicSinglePreTradeStrategy implements SinglePreTradeStrategy {
	private static final BigDecimal O_SPREAD_FLOOR = new BigDecimal("0.005"); // 0.5%
	private static final BigDecimal F_SPREAD_FLOOR = new BigDecimal("0"); // 0%

	public static BigDecimal oSpread(ExchangeSnapshot sn) {
		BigDecimal futuresAsk = sn.futuresSnapshot().bookTicker().askPrice();
		BigDecimal spotBid = sn.spotSnapshot().bookTicker().bidPrice();
		BigDecimal notional = futuresAsk.add(spotBid).divide(BigDecimal.TWO, RoundingMode.HALF_EVEN);

		return spotBid.subtract(futuresAsk).divide(notional, 8, RoundingMode.HALF_EVEN);
	}

	public static BigDecimal fSpread(ExchangeSnapshot sn) {
		return sn.futuresSnapshot().fundingRate().rate().negate();
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
		BigDecimal spread = oSpread(data.snapshot());
		if (spread.compareTo(BigDecimal.ZERO) >= 0) return new TradeDirections(TradeMarket.FUTURES, TradeMarket.SPOT);
		return new TradeDirections(TradeMarket.SPOT, TradeMarket.FUTURES);
	}

	@Override
	public boolean goodToEnter(ExchangeData data) {
		BigDecimal absOSpread = oSpread(data.snapshot()).abs();
		BigDecimal absFSpread = fSpread(data.snapshot()).abs();
		BigDecimal totalFees = totalFees(data.constantData());
		return absOSpread.subtract(totalFees).compareTo(O_SPREAD_FLOOR) > 0 && absFSpread.compareTo(F_SPREAD_FLOOR) > 0;
	}

	@Override
	public BigDecimal expectedGain(ExchangeData data) {
		BigDecimal absOSpread = oSpread(data.snapshot()).abs();
		BigDecimal absFSpread = fSpread(data.snapshot()).abs();
		return absOSpread.add(absFSpread);
	}
}
