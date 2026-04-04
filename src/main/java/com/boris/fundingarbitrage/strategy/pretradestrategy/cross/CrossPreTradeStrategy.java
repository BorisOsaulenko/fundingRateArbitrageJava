package com.boris.fundingarbitrage.strategy.pretradestrategy.cross;

import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;

import java.math.BigDecimal;

public interface CrossPreTradeStrategy {
	boolean goodToEnter(ExchangeData longData, ExchangeData shortData);

	TradeDirections getDirections(ExchangeData longData, ExchangeData shortData);

	BigDecimal expectedGain(ExchangeData longData, ExchangeData shortData);

	boolean requiredSpot();

	boolean requiredFutures();
}
