package com.boris.fundingarbitrage.mocks;

import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;
import lombok.Getter;
import lombok.Setter;

public class FakeInTradeStrategy extends InTradeStrategy {
	@Getter private Snapshot lastLongCurrent;
	@Getter private Snapshot lastShortCurrent;
	@Setter @Getter private boolean shouldExitTrade = true;

	@Override
	protected void accountForFundingEvent(FuturesSnapshot sn, boolean isLong) {
	}

	@Override
	public boolean shouldExitTrade(Snapshot longCurrent, Snapshot shortCurrent) {
		this.lastLongCurrent = longCurrent;
		this.lastShortCurrent = shortCurrent;
		return shouldExitTrade;
	}
}
