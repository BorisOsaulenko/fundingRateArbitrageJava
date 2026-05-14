package com.boris.fundingarbitrage.logic.coincapper;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.coinfilter.ConstantDataRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CoinCapper {
	private final CoinAvailabilityRecord coinAvailability;
	private final IOpportunityAnalyzer opportunityAnalyzer;
	private final ExchangeCoinMap<FuturesSnapshot> initialFuturesSnapshots;
	private final ExchangeCoinMap<SpotSnapshot> initialSpotSnapshots;
	private final ConstantDataRecord constantDataRecord;
	private final int maxCoinAmount;

	public CoinCapper(
					IOpportunityAnalyzer opportunityAnalyzer,
					CoinFilterResult filterResult,
					int maxCoinAmount
	) {
		this.coinAvailability = filterResult.coinAvailability();
		this.opportunityAnalyzer = opportunityAnalyzer;
		this.initialFuturesSnapshots = filterResult.initialFuturesSnapshots();
		this.initialSpotSnapshots = filterResult.initialSpotSnapshots();
		this.constantDataRecord = filterResult.constantDataRecord();
		this.maxCoinAmount = maxCoinAmount;
	}

	Snapshot getSnapshot(BaseExchange ex, String coin, TradeMarket market) {
		return switch (market) {
			case SPOT -> initialSpotSnapshots.get(ex, coin);
			case FUTURES -> initialFuturesSnapshots.get(ex, coin);
		};
	}

	public CompletableFuture<Void> capCoins() {
		if (maxCoinAmount <= coinAvailability.coinCount()) return CompletableFuture.completedFuture(null);
		return opportunityAnalyzer.processCoins(this::getSnapshot)
						.thenApply(opps -> opps.sortDesc(Comparator.comparing(CoinOpportunity::expectedGain)))
						.thenApply(sortedOpps -> sortedOpps.stream()
										.skip(maxCoinAmount)
										.map(Map.Entry::getKey)
										.collect(Collectors.toSet())
						)
						.thenAccept(coins -> {
							coinAvailability.removeByCoins(coins);
							constantDataRecord.removeByCoins(coins);
						});
	}
}
