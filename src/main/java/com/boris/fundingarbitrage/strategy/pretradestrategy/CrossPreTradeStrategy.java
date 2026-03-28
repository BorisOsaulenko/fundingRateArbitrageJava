package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.math.BigDecimal;

public interface CrossPreTradeStrategy {
	boolean goodToEnter(ExchangeData longData, ExchangeData shortData);

	TradeMarket getDirections(ExchangeData longData, ExchangeData shortData);

	BigDecimal expectedGain(ExchangeData longData, ExchangeData shortData);
}
