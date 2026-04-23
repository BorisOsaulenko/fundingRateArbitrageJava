package com.boris.fundingarbitrage.strategy.intradestrategy.factory;

import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;

public abstract class InTradeStrategyFactory {
	public abstract InTradeStrategy create(
					ExchangeData longEnter,
					ExchangeData shortEnter
	);
}
