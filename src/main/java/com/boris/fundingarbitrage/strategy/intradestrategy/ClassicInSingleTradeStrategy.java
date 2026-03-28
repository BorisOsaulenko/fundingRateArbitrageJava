package com.boris.fundingarbitrage.strategy.intradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

public class ClassicInSingleTradeStrategy implements InSingleTradeStrategy {
	@Override
	public boolean shouldExitTrade(ExchangeSnapshot current) {
		return false;
	}
}
