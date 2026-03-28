package com.boris.fundingarbitrage.coinfilter;

import java.math.BigDecimal;
import java.util.Comparator;

public record CoinFilterConfig(
				BigDecimal min24hVolumeUsdt,
				BigDecimal maxAffordablePrice,
				Comparator<ArbitrageData> coinsComparator
) {
}
