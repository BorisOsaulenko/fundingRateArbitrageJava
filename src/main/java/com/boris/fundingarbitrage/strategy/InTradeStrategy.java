package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class InTradeStrategy {
	@Getter protected final List<ArbitrageSnapshot> fundingSnapshots = new CopyOnWriteArrayList<>();
	@Getter protected final ArbitrageSnapshot enterSnapshot;

	public InTradeStrategy(ArbitrageSnapshot enterSnapshot) {
		this.enterSnapshot = enterSnapshot;
	}

	public abstract boolean shouldExitTrade(ArbitrageSnapshot current);

	public void addFundingSnapshot(ArbitrageSnapshot fundingSnapshot) {
		this.fundingSnapshots.add(fundingSnapshot);
	}
}
