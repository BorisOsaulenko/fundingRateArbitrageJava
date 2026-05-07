package com.boris.fundingarbitrage.logic.opportunityanalyzer;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.exchange.exchangedata.FuturesExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.SpotExchangeData;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public interface IOpportunityAnalyzer {
	CompletableFuture<CoinVector<CoinOpportunity>> processCoins(
					BiFunction<BaseExchange, String, FuturesExchangeData> futuresExchangeDataExtractor,
					BiFunction<BaseExchange, String, SpotExchangeData> spotExchangeDataExtractor
	);

	void shutdown();
}
