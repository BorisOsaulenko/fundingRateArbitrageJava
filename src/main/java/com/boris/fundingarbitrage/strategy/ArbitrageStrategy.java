package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;

public abstract class ArbitrageStrategy {
	private ArbitrageSnapshot enterSnapshot = null;
	private final List<ArbitrageSnapshot> fundingSnapshots = new ArrayList<>();

	public abstract boolean snapshotBetter(
					ArbitrageSnapshot first,
					ArbitrageSnapshot second
	); // returns true if first snapshot is better than second

	// returns true if snapshot is good enough to execute a trade
	public abstract boolean snapshotGoodEnough(ArbitrageSnapshot snapshot);

	public abstract boolean shouldExitTrade(ArbitrageSnapshot current);

	public void setEnterSnapshot(ArbitrageSnapshot enterSnapshot) {
		if (this.enterSnapshot != null) {
			Logger.error("ArbitrageStrategy has already been entered.");
			return;
		}
		this.enterSnapshot = enterSnapshot;
	}

	public void addFundingSnapshot(ArbitrageSnapshot fundingSnapshot) {
		this.fundingSnapshots.add(fundingSnapshot);
	}
}
