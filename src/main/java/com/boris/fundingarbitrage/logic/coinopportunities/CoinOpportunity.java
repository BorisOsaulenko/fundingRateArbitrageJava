package com.boris.fundingarbitrage.logic.coinopportunities;

import java.math.BigDecimal;

public sealed interface CoinOpportunity
				permits SingleCoinOpportunity, CrossCoinOpportunity {
	BigDecimal expectedGain();
}