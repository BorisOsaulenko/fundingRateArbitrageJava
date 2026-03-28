package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

public abstract class InSingleTradeStrategy extends BaseInTradeStrategy {
	public abstract boolean shouldExitTrade(ExchangeSnapshot current);
}
