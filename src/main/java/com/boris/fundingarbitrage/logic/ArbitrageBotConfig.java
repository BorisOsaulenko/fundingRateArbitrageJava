package com.boris.fundingarbitrage.logic;

import java.time.Duration;
import java.util.Set;

public record ArbitrageBotConfig(
				Set<String> coins,
				double legUsdtAmount,
// the size of trade on one leg.
				int leverage,
				Duration timeForWithdrawals,
// Bot will move assets to the long and short exchanges. Withdrawals + deposit registration take time. ~ 20 min recommended
				Duration beforeEnter,
// The trades are entered in (settlement - beforeEnter) timestamp
				Duration afterEnter
// Some exchanges only apply funding only some time after the settlement
) {}