package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;

import java.math.BigDecimal;

public record CoinOpportunity(
				ExchangePair exchanges,
				BigDecimal expectedGain,
				ExchangeData longData,
				ExchangeData shortData,
				boolean goodEnough,
				TradeDirections directions
) {
}
