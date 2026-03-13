package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;

import java.math.BigDecimal;
import java.util.Comparator;

public record CoinFilterConfig(
				BigDecimal min24hVolumeUsdt,
				BigDecimal maxAffordablePrice,
				int maxCoinAmount,
				Comparator<ArbitrageSnapshot> comparator
) {
}
