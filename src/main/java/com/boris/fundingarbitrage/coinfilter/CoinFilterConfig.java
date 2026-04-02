package com.boris.fundingarbitrage.coinfilter;

import java.math.BigDecimal;

public record CoinFilterConfig(
				BigDecimal min24hVolumeUsdt,
				BigDecimal maxAffordablePrice
) {
}
