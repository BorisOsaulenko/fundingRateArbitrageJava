package com.boris.fundingarbitrage.model.exchange.exchangedata;

import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;

public record SpotExchangeData(
				SpotConstantData constantData,
				SpotSnapshot snapshot
) implements ExchangeData {
	@Override
	public TradeMarket market() {
		return TradeMarket.SPOT;
	}
}
