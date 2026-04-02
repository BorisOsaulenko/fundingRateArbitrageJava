package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeData;

import java.math.BigDecimal;

public interface SinglePreTradeStrategy {
	boolean goodToEnter(ExchangeData data);

	TradeDirections getDirections(ExchangeData data);

	BigDecimal expectedGain(ExchangeData data);
}
