package com.boris.fundingarbitrage.model.exchange.exchangedata;

import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;

public sealed interface ExchangeData permits FuturesExchangeData, SpotExchangeData {
	static ExchangeData of(ConstantData constantData, Snapshot snapshot) {
		if (snapshot.market() != constantData.market())
			throw new RuntimeException("Snapshot and ConstantData must be of same TradeMarket.");

		return switch (snapshot.market()) {
			case SPOT -> new SpotExchangeData((SpotConstantData) constantData, (SpotSnapshot) snapshot);
			case FUTURES -> new FuturesExchangeData((FuturesConstantData) constantData, (FuturesSnapshot) snapshot);
		};
	}

	Snapshot snapshot();

	ConstantData constantData();

	TradeMarket market();
}
