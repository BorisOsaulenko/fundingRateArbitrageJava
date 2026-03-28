package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

public abstract class InCrossTradeStrategy extends BaseInTradeStrategy {
	public abstract boolean shouldExitTrade(ExchangeSnapshot longCurrent, ExchangeSnapshot shortCurrent);
}
