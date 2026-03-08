package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;

public interface PreTradeStrategy {
	boolean snapshotGoodEnough(ArbitrageSnapshot snapshot);

	int compareSnapshots(ArbitrageSnapshot snapshot1, ArbitrageSnapshot snapshot2);
}
