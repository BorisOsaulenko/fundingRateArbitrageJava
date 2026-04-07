package com.boris.fundingarbitrage.logic.coinopportunities;

import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.math.BigDecimal;

public record SingleCoinOpportunity(
				BaseExchange exchange,
				BigDecimal expectedGain,
				ExchangeData data
) implements CoinOpportunity {
}
