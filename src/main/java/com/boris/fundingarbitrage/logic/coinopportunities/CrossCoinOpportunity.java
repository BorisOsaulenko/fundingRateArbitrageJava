package com.boris.fundingarbitrage.logic.coinopportunities;

import com.boris.fundingarbitrage.model.exchange.ExchangePair;

import java.math.BigDecimal;

public record CrossCoinOpportunity(
				ExchangePair exchanges,
				BigDecimal expectedGain,
				ExchangeData longData,
				ExchangeData shortData
) implements CoinOpportunity {
}
