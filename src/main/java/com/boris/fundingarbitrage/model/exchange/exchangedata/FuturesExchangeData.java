package com.boris.fundingarbitrage.model.exchange.exchangedata;

import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;

public record FuturesExchangeData(
				FuturesConstantData constantData,
				FuturesSnapshot snapshot
) implements ExchangeData {
	@Override
	public TradeMarket market() {
		return TradeMarket.FUTURES;
	}
}
