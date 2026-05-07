package com.boris.fundingarbitrage.logic.coincapper;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.coinfilter.ConstantDataRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.exchange.exchangedata.FuturesExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.SpotExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;

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

	FuturesExchangeData getFuturesData(BaseExchange ex, String coin) {
		return new FuturesExchangeData(
						constantDataRecord.getFuturesConstantData(ex, coin),
						initialFuturesSnapshots.get(ex, coin)
		);
	}

	SpotExchangeData getSpot(BaseExchange ex, String coin) {
		return new SpotExchangeData(
						constantDataRecord.getSpotConstantData(ex, coin),
						initialSpotSnapshots.get(ex, coin)
		);
	}

	public CompletableFuture<Void> capCoins() {
		if (maxCoinAmount <= coinAvailability.coinCount()) return CompletableFuture.completedFuture(null);
		return opportunityAnalyzer.processCoins(this::getFuturesData, this::getSpot)
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
