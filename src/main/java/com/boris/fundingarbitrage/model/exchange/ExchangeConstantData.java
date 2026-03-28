package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.math.BigDecimal;

public record ExchangeConstantData(
				FuturesConstantData futuresConstantData,
				SpotConstantData spotConstantData
) {
	public Fees fees(TradeMarket market) {
		if (market == TradeMarket.FUTURES) return futuresConstantData.fees();
		else return spotConstantData.fees();
	}

	public BigDecimal lotSize(TradeMarket market) {
		if (market == TradeMarket.FUTURES) return futuresConstantData.lotSize();
		else return spotConstantData.lotSize();
	}

	public int fundingInterval() {
		return futuresConstantData.fundingInterval();
	}
}
