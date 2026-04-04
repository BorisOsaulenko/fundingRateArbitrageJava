package com.boris.fundingarbitrage.strategy;

import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;

public record TradeStrategy<T extends InTradeStrategy>(PreTradeStrategy preTradeStrategy, Class<T> inTradeStrategy) {
}
