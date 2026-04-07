package com.boris.fundingarbitrage.strategy.pretradestrategy.single;

import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;

import java.math.BigDecimal;

public sealed interface SinglePreTradeStrategy permits ClassicSinglePreTradeStrategy {
	boolean goodToEnter(ExchangeData data);

	TradeDirections getDirections(ExchangeData data);

	BigDecimal expectedGain(ExchangeData data);

	boolean requiredSpot();

	boolean requiredFutures();
}
