package com.boris.fundingarbitrage.logic;

import java.util.Set;

public record ArbitrageBotConfig(
				Set<String> coins,
				// the size of trade on one leg.
				double legUsdtAmount,
				int leverage,
				// Bot will log the current best arb snapshots for coins + best arb snapshot overall. Set to 0 to disable logging
				int loggingIntervalSeconds,
				// Bot logs logBestArbSnapshotsAmount of the best arbitrage opportunities
				int logBestArbSnapshotsAmount
) {
}