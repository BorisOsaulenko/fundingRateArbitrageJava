package com.boris.fundingarbitrage.strategy.pretradestrategy;

import com.boris.fundingarbitrage.model.exchange.ExchangeData;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.math.BigDecimal;

public interface SinglePreTradeStrategy {
	boolean goodToEnter(ExchangeData data);

	TradeMarket getDirections(ExchangeData data);

	BigDecimal expectedGain(ExchangeData data);
}
