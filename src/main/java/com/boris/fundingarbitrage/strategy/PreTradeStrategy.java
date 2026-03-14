package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;

public interface PreTradeStrategy {
	boolean arbDataGoodEnough(ArbitrageData snapshot);

	int compareArbData(ArbitrageData first, ArbitrageData second);
}
