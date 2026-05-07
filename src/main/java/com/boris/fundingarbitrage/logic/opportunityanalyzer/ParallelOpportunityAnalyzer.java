package com.boris.fundingarbitrage.logic.opportunityanalyzer;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.FuturesExchangeData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.SpotExchangeData;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ParallelOpportunityAnalyzer implements IOpportunityAnalyzer {
	private final ExecutorService cpuPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	private final CoinAvailabilityRecord coinAvailability;
	private final PreTradeStrategy preTradeStrategy;

	public ParallelOpportunityAnalyzer(CoinAvailabilityRecord coinAvailability, PreTradeStrategy preTradeStrategy) {
		this.coinAvailability = coinAvailability;
		this.preTradeStrategy = preTradeStrategy;
	}

	CoinOpportunity computeBestCoinOpportunity(
					ExchangePair exchanges,
					String coin,
					BiFunction<BaseExchange, String, FuturesExchangeData> futuresExchangeDataExtractor,
					BiFunction<BaseExchange, String, SpotExchangeData> spotExchangeDataExtractor
	) {
		FuturesExchangeData longFuturesData = futuresExchangeDataExtractor.apply(exchanges.longEx(), coin);
		SpotExchangeData longSpotData = spotExchangeDataExtractor.apply(exchanges.longEx(), coin);
		FuturesExchangeData shortFuturesData = futuresExchangeDataExtractor.apply(exchanges.shortEx(), coin);
		SpotExchangeData shortSpotData = spotExchangeDataExtractor.apply(exchanges.shortEx(), coin);

		class Best {
			BigDecimal gain = null;
			TradeDirections tradeDirections = null;
			ExchangeData longData = null, shortData = null;
		}
		Best best = new Best();

		BiConsumer<ExchangeData, ExchangeData> update = (ld, sd) -> {
			if (ld == null || sd == null) return;
			BigDecimal g = preTradeStrategy.expectedGain(ld, sd);
			if (best.gain == null || g.compareTo(best.gain) >= 0) {
				best.gain = g;
				best.longData = ld;
				best.shortData = sd;
				best.tradeDirections = new TradeDirections(ld.market(), sd.market());
			}
		};

		if (exchanges.longEx() == exchanges.shortEx()) {
			if (longSpotData == null || longFuturesData == null) return null;
			update.accept(longSpotData, longFuturesData);
			update.accept(longFuturesData, longSpotData);
		} else {
			if (longSpotData == null && longFuturesData == null) return null;
			if (shortSpotData == null && shortFuturesData == null) return null;
			update.accept(longSpotData, shortFuturesData);
			update.accept(longSpotData, shortSpotData);
			update.accept(longFuturesData, shortFuturesData);
			update.accept(longFuturesData, shortSpotData);
		}

		return new CoinOpportunity(
						new ExchangePair(exchanges.longEx(), exchanges.shortEx()),
						best.gain,
						best.longData,
						best.shortData,
						best.tradeDirections
		);
	}

	CoinOpportunity computeBestCoinOpportunity(
					String coin,
					BiFunction<BaseExchange, String, FuturesExchangeData> futuresExchangeDataExtractor,
					BiFunction<BaseExchange, String, SpotExchangeData> spotExchangeDataExtractor
	) {
		Set<BaseExchange> availableExchanges = coinAvailability.getExchanges(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		CoinOpportunity bestOp = null;

		for (BaseExchange longEx : availableExchanges) {
			for (BaseExchange shortEx : availableExchanges) {
				CoinOpportunity bestForExchanges = computeBestCoinOpportunity(
								new ExchangePair(longEx, shortEx),
								coin,
								futuresExchangeDataExtractor,
								spotExchangeDataExtractor
				);
				if (bestForExchanges == null) continue;
				if (bestOp == null || bestOp.expectedGain().compareTo(bestForExchanges.expectedGain()) < 0)
					bestOp = bestForExchanges;
			}
		}

		return bestOp;
	}

	public CompletableFuture<CoinVector<CoinOpportunity>> processCoins(
					BiFunction<BaseExchange, String, FuturesExchangeData> futuresExchangeDataExtractor,
					BiFunction<BaseExchange, String, SpotExchangeData> spotExchangeDataExtractor
	) {
		CoinVector<CoinOpportunity> result = new CoinVector<>();
		List<CompletableFuture<Void>> futures = coinAvailability.getCoins().stream().map(coin ->
										CompletableFuture.runAsync(
														() -> {
															CoinOpportunity bestOp = computeBestCoinOpportunity(
																			coin,
																			futuresExchangeDataExtractor,
																			spotExchangeDataExtractor
															);
															if (bestOp != null) result.put(coin, bestOp);
														},
														cpuPool
										)
						)
						.toList();

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> result);
	}

	public void shutdown() {
		cpuPool.shutdownNow();
	}
}
