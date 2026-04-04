package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

public abstract non-sealed class InSingleTradeStrategy extends InTradeStrategy {
	public abstract boolean shouldExitTrade(ExchangeSnapshot current);
}
