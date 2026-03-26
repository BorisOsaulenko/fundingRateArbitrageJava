package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;

public interface PreTradeStrategy {
	boolean goodToEnter(ArbitrageData snapshot);

	int compareArbData(ArbitrageData first, ArbitrageData second);
}
