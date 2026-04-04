package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeData;

import java.math.BigDecimal;

public sealed interface CrossPreTradeStrategy permits ClassicCrossPreTradeStrategy {
	boolean goodToEnter(ExchangeData longData, ExchangeData shortData);

	TradeDirections getDirections(ExchangeData longData, ExchangeData shortData);

	BigDecimal expectedGain(ExchangeData longData, ExchangeData shortData);
}
