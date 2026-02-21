package com.boris.fundingarbitrage.coinfilter;

import java.math.BigDecimal;

public record CoinFilterConfig(
				double min24hVolumeUsdt, BigDecimal maxAffordablePrice
) {}
