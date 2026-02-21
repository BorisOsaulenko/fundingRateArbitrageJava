package com.boris.fundingarbitrage.coinfilter;

public record CoinFilterConfig(
	double min24hVolumeUsdt,
	double maxAffordablePrice
) {}
