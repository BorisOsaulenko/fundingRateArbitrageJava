package com.boris.fundingarbitrage.logic;

import java.time.Duration;
import java.util.Set;

public record ArbitrageBotConfig(
				Set<String> coins,
				// the size of trade on one leg.
				double legUsdtAmount,
				int leverage,
				// Bot will move assets to the long and short exchanges. Withdrawals + deposit registration take time. ~ 20 min recommended
				Duration timeForWithdrawals,
				// The trades are entered in (settlement - beforeEnter) timestamp
				Duration beforeEnter,
				// Some exchanges only apply funding only some time after the settlement
				Duration afterEnter,
				// Bot will log the current best arb snapshots for coins + best arb snapshot overall. Set to 0 to disable logging
				int loggingIntervalSeconds,
				// Bot logs logBestArbSnapshotsAmount of the best arbitrage opportunities
				int logBestArbSnapshotsAmount
) {}