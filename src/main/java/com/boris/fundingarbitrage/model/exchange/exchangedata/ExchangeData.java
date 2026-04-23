package com.boris.fundingarbitrage.model.exchange.exchangedata;

import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;

public sealed interface ExchangeData permits FuturesExchangeData, SpotExchangeData {
	Snapshot snapshot();

	ConstantData constantData();

	TradeMarket market();
}
