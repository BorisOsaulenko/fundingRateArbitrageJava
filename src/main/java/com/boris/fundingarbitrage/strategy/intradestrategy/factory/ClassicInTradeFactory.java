package com.boris.fundingarbitrage.strategy.intradestrategy.factory;

import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.strategy.intradestrategy.ClassicInTradeStrategy;
import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;

public class ClassicInTradeFactory extends InTradeStrategyFactory {
	@Override
	public InTradeStrategy create(ExchangeData longEnter, ExchangeData shortEnter) {
		return new ClassicInTradeStrategy(longEnter, shortEnter);
	}
}
