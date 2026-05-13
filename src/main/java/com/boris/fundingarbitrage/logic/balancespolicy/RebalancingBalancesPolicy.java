package com.boris.fundingarbitrage.logic.balancespolicy;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public class RebalancingBalancesPolicy implements IBalancesPolicy {
	private static final BigDecimal REBALANCE_THRESHOLD = new BigDecimal("0.3"); // 30%
	private final Logger log = LoggerFactory.getLogger(RebalancingBalancesPolicy.class);
	private final BigDecimal legUsdtAmount;
	private final BigDecimal safetyMargin;

	public RebalancingBalancesPolicy(@NonNull BigDecimal legUsdtAmount, @NonNull BigDecimal safetyMargin) {
		this.legUsdtAmount = legUsdtAmount;
		this.safetyMargin = safetyMargin;
	}

	@Override
	public void validateBalancesMap(Map<BaseExchange, ExchangeBalance> balanceMap) {
		BigDecimal balancesSum = BigDecimal.ZERO;
		BigDecimal maxBalance = BigDecimal.ZERO;
		BigDecimal minBalance = new BigDecimal(Long.MAX_VALUE);

		BigDecimal requiredTotal = legUsdtAmount
						.add(safetyMargin)
						.multiply(BigDecimal.TWO); // need same amount on futures and spot

		for (Map.Entry<BaseExchange, ExchangeBalance> entry : balanceMap.entrySet()) {
			BaseExchange ex = entry.getKey();
			BigDecimal spotBalance = entry.getValue().spotFreeUsdt();
			BigDecimal futuresBalance = entry.getValue().futuresFreeUsdt();
			BigDecimal totalBalance = spotBalance.add(futuresBalance);

			balancesSum = balancesSum.add(totalBalance);
			maxBalance = totalBalance.max(maxBalance);
			minBalance = totalBalance.min(minBalance);

			if (totalBalance.compareTo(requiredTotal) < 0) {
				log.info("Not enough balance to start arbitrage on {}", ex.name());
				throw new RuntimeException("Not enough balance to start arbitrage on " + ex.name());
			}
		}

		BigDecimal avgBalance = balancesSum.divide(new BigDecimal(balanceMap.size()), RoundingMode.HALF_UP);
		BigDecimal maxDiff = maxBalance.subtract(minBalance);
		BigDecimal maxDiffRelative = maxDiff.divide(avgBalance, RoundingMode.HALF_UP);
		if (maxDiffRelative.compareTo(REBALANCE_THRESHOLD) > 0) {
			log.info("Rebalance is suggested. Max balance diff is {} compared to average balance", maxDiffRelative);
		}

	}
}
