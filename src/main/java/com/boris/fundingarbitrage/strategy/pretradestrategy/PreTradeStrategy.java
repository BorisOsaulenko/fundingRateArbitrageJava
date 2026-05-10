package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;

import java.math.BigDecimal;

public interface PreTradeStrategy {
	boolean goodToEnter(ExchangeData longData, ExchangeData shortData);

	BigDecimal expectedGain(ExchangeData longData, ExchangeData shortData);

	String getDescription(ExchangeData longData, ExchangeData shortData);
}
