package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public abstract sealed class InTradeStrategy permits InCrossTradeStrategy, InSingleTradeStrategy {
	@Getter private final List<ExchangeSnapshot> longFundingSnapshots = new ArrayList<>();
	@Getter private final List<ExchangeSnapshot> shortFundingSnapshots = new ArrayList<>();

	public void registerFunding(ExchangeSnapshot sn, boolean isLong) {
		if (isLong) longFundingSnapshots.add(sn);
		else shortFundingSnapshots.add(sn);

		accountForFundingEvent(sn, isLong);
	}

	protected abstract void accountForFundingEvent(ExchangeSnapshot sn, boolean isLong);
}