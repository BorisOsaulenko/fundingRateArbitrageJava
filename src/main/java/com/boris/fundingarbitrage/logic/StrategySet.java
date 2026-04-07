package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.strategy.intradestrategy.InCrossTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.cross.CrossPreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.single.SinglePreTradeStrategy;

public record StrategySet(
				CrossPreTradeStrategy crossPreTradeStrategy,
				SinglePreTradeStrategy singlePreTradeStrategy,
				Class<InCrossTradeStrategy> inCrossTradeStrategyClass,

) {
}
