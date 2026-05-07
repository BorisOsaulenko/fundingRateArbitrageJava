package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.model.Validations;

import java.math.BigDecimal;

public record ArbitrageBotConfig(
				// the size of trade on one leg.
				BigDecimal legUsdtAmount,
				BigDecimal safetyMargin,
				int leverage,
				// Bot will log the current best arb snapshots for coins + best arb snapshot overall. Set to 0 to disable logging
				int loggingIntervalSeconds,
				// Bot logs logBestArbSnapshotsAmount of the best arbitrage opportunities
				int logBestArbSnapshotsAmount
) {
	public ArbitrageBotConfig {
		Validations.requirePositive(legUsdtAmount, "Leg USDT amount");
		Validations.requirePositive(leverage, "Leverage");
		Validations.requireNonNegative(loggingIntervalSeconds, "Logging interval");
		Validations.requirePositive(logBestArbSnapshotsAmount, "Log best arb snapshots amount");
	}
}