package com.boris.fundingarbitrage.logic.opportunityanalyzer;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.apache.commons.lang3.function.TriFunction;

import java.util.concurrent.CompletableFuture;

public interface IOpportunityAnalyzer {
	CompletableFuture<CoinVector<CoinOpportunity>> processCoins(
					TriFunction<BaseExchange, String, TradeMarket, Snapshot> snapshotExtractor
	);

	void shutdown();
}
