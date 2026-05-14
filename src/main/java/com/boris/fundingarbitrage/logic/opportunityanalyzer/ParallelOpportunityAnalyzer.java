package com.boris.fundingarbitrage.logic.opportunityanalyzer;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.coinfilter.ConstantDataRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.apache.commons.lang3.function.TriFunction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class ParallelOpportunityAnalyzer implements IOpportunityAnalyzer {
	private final ExecutorService cpuPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	private final CoinAvailabilityRecord coinAvailability;
	private final PreTradeStrategy preTradeStrategy;
	private final ConstantDataRecord constantDataRecord;

	public ParallelOpportunityAnalyzer(
					CoinAvailabilityRecord coinAvailability,
					ConstantDataRecord constantDataRecord,
					PreTradeStrategy preTradeStrategy
	) {
		this.coinAvailability = coinAvailability;
		this.constantDataRecord = constantDataRecord;
		this.preTradeStrategy = preTradeStrategy;
	}

	private ExchangeData getExchangeData(BaseExchange ex, String coin, Snapshot snapshot) {
		ConstantData cd = constantDataRecord.getConstantData(ex, coin, snapshot.market());
		return ExchangeData.of(cd, snapshot);
	}

	CoinOpportunity computeBestCoinOpportunity(
					ExchangePair exchanges,
					String coin,
					TriFunction<BaseExchange, String, TradeMarket, Snapshot> snapshotExtractor
	) {
		Snapshot longFuturesData = snapshotExtractor.apply(exchanges.longEx(), coin, TradeMarket.FUTURES);
		Snapshot longSpotData = snapshotExtractor.apply(exchanges.longEx(), coin, TradeMarket.SPOT);
		Snapshot shortFuturesData = snapshotExtractor.apply(exchanges.shortEx(), coin, TradeMarket.FUTURES);
		Snapshot shortSpotData = snapshotExtractor.apply(exchanges.shortEx(), coin, TradeMarket.SPOT);

		class Best {
			BigDecimal gain = null;
			TradeDirections tradeDirections = null;
			ExchangeData longData = null, shortData = null;
		}
		Best best = new Best();

		BiConsumer<Snapshot, Snapshot> update = (longSnapshot, shortSnapshot) -> {
			if (longSnapshot == null || shortSnapshot == null) return;
			ExchangeData longExchangeData = getExchangeData(exchanges.longEx(), coin, longSnapshot);
			ExchangeData shortExchangeData = getExchangeData(exchanges.shortEx(), coin, shortSnapshot);
			BigDecimal g = preTradeStrategy.expectedGain(longExchangeData, shortExchangeData);
			if (best.gain == null || g.compareTo(best.gain) >= 0) {
				best.gain = g;
				best.longData = longExchangeData;
				best.shortData = shortExchangeData;
				best.tradeDirections = new TradeDirections(longSnapshot.market(), shortSnapshot.market());
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
						preTradeStrategy.goodToEnter(best.longData, best.shortData),
						best.tradeDirections
		);
	}

	CoinOpportunity computeBestCoinOpportunity(
					String coin,
					TriFunction<BaseExchange, String, TradeMarket, Snapshot> snapshotExtractor
	) {
		Set<BaseExchange> availableExchanges = coinAvailability.getExchanges(coin);
		if (availableExchanges == null) throw new IllegalStateException("Available exchanges for " + coin + " not found");

		CoinOpportunity bestOp = null;

		for (BaseExchange longEx : availableExchanges) {
			for (BaseExchange shortEx : availableExchanges) {
				CoinOpportunity bestForExchanges = computeBestCoinOpportunity(
								new ExchangePair(longEx, shortEx),
								coin,
								snapshotExtractor
				);
				if (bestForExchanges == null) continue;
				if (bestOp == null || bestOp.expectedGain().compareTo(bestForExchanges.expectedGain()) < 0)
					bestOp = bestForExchanges;
			}
		}

		return bestOp;
	}

	public CompletableFuture<CoinVector<CoinOpportunity>> processCoins(
					TriFunction<BaseExchange, String, TradeMarket, Snapshot> snapshotExtractor
	) {
		CoinVector<CoinOpportunity> result = new CoinVector<>();
		List<CompletableFuture<Void>> futures = coinAvailability.getCoins().stream().map(coin ->
										CompletableFuture.runAsync(
														() -> {
															CoinOpportunity bestOp = computeBestCoinOpportunity(coin, snapshotExtractor);
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
