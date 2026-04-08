package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.single.SinglePreTradeStrategy;

public record StrategySet(
				PreTradeStrategy crossPreTradeStrategy,
				SinglePreTradeStrategy singlePreTradeStrategy,
				Class<InCrossTradeStrategy> inCrossTradeStrategyClass,

) {
}
