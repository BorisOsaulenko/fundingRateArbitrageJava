package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public abstract class InTradeStrategy {
	@Getter private final List<FuturesSnapshot> longFundingSnapshots = new ArrayList<>();
	@Getter private final List<FuturesSnapshot> shortFundingSnapshots = new ArrayList<>();

	public void registerFunding(FuturesSnapshot sn, boolean isLong) {
		if (isLong) longFundingSnapshots.add(sn);
		else shortFundingSnapshots.add(sn);

		accountForFundingEvent(sn, isLong);
	}

	protected abstract void accountForFundingEvent(FuturesSnapshot sn, boolean isLong);

	public abstract boolean shouldExitTrade(Snapshot longCurrent, Snapshot shortCurrent);
}