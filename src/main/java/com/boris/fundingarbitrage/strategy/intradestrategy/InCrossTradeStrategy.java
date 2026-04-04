package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

public abstract non-sealed class InCrossTradeStrategy extends InTradeStrategy {
	public abstract boolean shouldExitTrade(ExchangeSnapshot longCurrent, ExchangeSnapshot shortCurrent);
}
