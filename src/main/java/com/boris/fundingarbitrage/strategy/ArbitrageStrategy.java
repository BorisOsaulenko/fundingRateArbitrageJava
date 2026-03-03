package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ArbitrageStrategy {
	private final List<ArbitrageSnapshot> fundingSnapshots = new ArrayList<>();
	private ArbitrageSnapshot enterSnapshot = null;

	public abstract int compareSnapshots(
					ArbitrageSnapshot first,
					ArbitrageSnapshot second
	); // returns true if first snapshot is better than second

	// returns true if the snapshot is good enough to execute a trade
	public abstract boolean snapshotGoodEnough(ArbitrageSnapshot snapshot);

	// Locking = sending funds to the long and short exchanges.
	public abstract boolean shouldLockOnSnapshot(ArbitrageSnapshot snapshot);

	public abstract boolean shouldExitTrade(ArbitrageSnapshot current);

	public final void addFundingSnapshot(ArbitrageSnapshot fundingSnapshot) {
		this.fundingSnapshots.add(fundingSnapshot);
	}

	protected final ArbitrageSnapshot getEnterSnapshot() {
		return enterSnapshot;
	}

	public final void setEnterSnapshot(ArbitrageSnapshot enterSnapshot) {
		if (this.enterSnapshot != null) {
			Logger.error("ArbitrageStrategy has already been entered.");
			return;
		}
		this.enterSnapshot = enterSnapshot;
	}

	protected final List<ArbitrageSnapshot> getFundingSnapshots() {
		return Collections.unmodifiableList(fundingSnapshots);
	}
}
