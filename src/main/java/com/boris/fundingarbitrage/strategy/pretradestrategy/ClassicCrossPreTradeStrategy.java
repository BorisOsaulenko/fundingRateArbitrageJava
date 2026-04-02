package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.SpotSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ClassicCrossPreTradeStrategy implements CrossPreTradeStrategy {
	private static final BigDecimal GAIN_FLOOR = new BigDecimal("0.005"); // 0.5%

	public static BigDecimal spotOSpread(SpotSnapshot longSn, SpotSnapshot shortSn) {
		BigDecimal longAsk = longSn.bookTicker().askPrice();
		BigDecimal shortBid = shortSn.bookTicker().bidPrice();
		BigDecimal notional = longAsk.add(shortBid).divide(BigDecimal.TWO, RoundingMode.HALF_EVEN);

		return shortBid.subtract(longAsk).divide(notional, 8, RoundingMode.HALF_EVEN);
	}

	public static BigDecimal totalSpotFees(SpotConstantData longCd, SpotConstantData shortCd) {
		BigDecimal result = BigDecimal.ZERO;
		result = result.add(longCd.fees().openTaker());
		result = result.add(shortCd.fees().openTaker());
		result = result.add(longCd.fees().closeTaker());
		result = result.add(shortCd.fees().closeTaker());

		return result;
	}

	@Override
	public BigDecimal expectedGain(ExchangeData longData, ExchangeData shortData) {
		BigDecimal spotOSpread = spotOSpread(longData.spotSnapshot(), shortData.spotSnapshot());
		BigDecimal totalSpotFees = totalSpotFees(longData.spotConstantData(), shortData.spotConstantData());
		return spotOSpread.subtract(totalSpotFees);
	}

	@Override
	public boolean goodToEnter(ExchangeData longData, ExchangeData shortData) {
		return expectedGain(longData, shortData).compareTo(GAIN_FLOOR) > 0;
	}

	@Override
	public TradeDirections getDirections(ExchangeData longData, ExchangeData shortData) {
		return new TradeDirections(TradeMarket.SPOT, TradeMarket.SPOT);
	}
}
