package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.util.logger.CoinVectorLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CoinMonitor {
	private final static Logger log = LoggerFactory.getLogger(CoinMonitor.class);
	private final ExchangeCoinMap<Funding> futuresFundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresBookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Mark> futuresMarkPrices = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickers = new ExchangeCoinMap<>();

	public final TimestampCompletionsScheduler completionAgent = new TimestampCompletionsScheduler(
					futuresFundingRates,
					futuresBookTickers,
					futuresMarkPrices,
					spotBookTickers
	);

	private final CoinFilterResult filterData;
	private final CoinAvailabilityRecord coinAvailability;
	private final IDataStream dataStream;

	public CoinMonitor(
					CoinFilterResult filterData,
					IDataStream dataStream
	) {
		this.filterData = filterData;
		this.coinAvailability = filterData.coinAvailability();
		this.dataStream = dataStream;
	}

	public void start() {
		log.info(coinAvailability.coinsByExchange().toString());

		dataStream.openWsConnections(coinAvailability.getExchanges())
						.thenRun(() -> log.debug("WS connections opened"));

		fillEmptyData();
		subscribeData();

		dataStream.onSteadyData(() -> {
			checkDataCompleteness();
			clearCoinsWithInsufficientExchanges();

			log.info("Coin monitor initialized:");
			CoinVectorLogger.logCoinVector(
							log, coinAvailability
											.exchangesByCoin()
											.transform((exchanges, _) -> exchanges.stream()
															.map(BaseExchange::name)
															.collect(Collectors.toSet()))
			);
		});
	}

	public CompletableFuture<Void> getInitFuture() {
		return dataStream.initFuture();
	}

	void checkDataCompleteness() {
		List<ExchangeCoinPair> toUnsubscribeFutures = new ArrayList<>();
		List<ExchangeCoinPair> toUnsubscribeSpot = new ArrayList<>();
		List<ExchangeCoinPair> toUnsubAll = new ArrayList<>();

		for (Map.Entry<BaseExchange, Set<String>> entry : coinAvailability.exchangeEntries()) {
			BaseExchange ex = entry.getKey();
			for (String coin : entry.getValue()) {
				BookTicker ticker = futuresBookTickers.get(ex, coin);
				Funding funding = futuresFundingRates.get(ex, coin);
				Mark mark = futuresMarkPrices.get(ex, coin);
				BookTicker spotTicker = spotBookTickers.get(ex, coin);

				boolean futuresTickerIncomplete = ticker == null || BookTicker.isPartiallyEmpty(ticker);
				boolean fundingIncomplete = funding == null || Funding.isPartiallyEmpty(funding);
				boolean markIncomplete = mark == null || Mark.isPartiallyEmpty(mark);
				boolean spotTickerIncomplete = spotTicker == null || BookTicker.isPartiallyEmpty(spotTicker);

				boolean shouldStayFutures = false;
				if (futuresTickerIncomplete) log.warn("Futures ticker incomplete for {} on {}", coin, ex.name());
				else if (fundingIncomplete) log.warn("Futures funding incomplete for {} on {}", coin, ex.name());
				else if (markIncomplete) log.warn("Futures mark incomplete for {} on {}", coin, ex.name());
				else shouldStayFutures = true;

				boolean shouldStaySpot = false;
				if (spotTickerIncomplete) log.warn("Spot ticker incomplete for {} on {}", coin, ex.name());
				else shouldStaySpot = true;

				ExchangeCoinPair pair = new ExchangeCoinPair(ex, coin);
				if (!shouldStayFutures && !shouldStaySpot) toUnsubAll.add(pair);
				else if (!shouldStayFutures) toUnsubscribeFutures.add(pair);
				else if (!shouldStaySpot) toUnsubscribeSpot.add(pair);
			}
		}

		if (!toUnsubAll.isEmpty()) unsubscribeAllEntries(toUnsubAll);
		if (!toUnsubscribeFutures.isEmpty()) unsubscribeFuturesEntries(toUnsubscribeFutures);
		if (!toUnsubscribeSpot.isEmpty()) unsubscribeSpotEntries(toUnsubscribeSpot);
	}

	void clearCoinsWithInsufficientExchanges() {
		for (String coin : coinAvailability.getCoins()) {
			Set<BaseExchange> exchanges = coinAvailability.getExchanges(coin);
			if (exchanges == null || exchanges.isEmpty()) {
				log.warn("Not enough exchanges support " + coin + ". Removing from monitoring.");
				coinAvailability.removeByCoin(coin);
			}
		}
	}

	void fillEmptyData() {
		coinAvailability.exchangesByCoin().forEach((coin, exchanges) -> {
			for (BaseExchange exchange : exchanges) {
				futuresFundingRates.put(exchange, coin, Funding.empty());
				futuresBookTickers.put(exchange, coin, BookTicker.empty());
				futuresMarkPrices.put(exchange, coin, Mark.empty());
				spotBookTickers.put(exchange, coin, BookTicker.empty());
			}
		});

		log.debug("Empty data filled");
	}

	void subscribeData() {
		for (Map.Entry<BaseExchange, Set<String>> entry : coinAvailability.exchangeEntries()) {
			BaseExchange ex = entry.getKey();
			Set<String> supportedCoins = new HashSet<>(entry.getValue());
			if (supportedCoins.isEmpty()) continue;

			Set<String> supportedOnFutures = supportedCoins.stream()
							.filter(coin -> coinAvailability.isFutures(ex, coin))
							.collect(Collectors.toSet());
			Set<String> supportedOnSpot = supportedCoins.stream()
							.filter(coin -> coinAvailability.isSpot(ex, coin))
							.collect(Collectors.toSet());

			if (!supportedOnFutures.isEmpty()) {
				Consumer<BookTickerPatch> bookHandler = createFuturesTickerHandler(ex);
				Consumer<FundingRatePatch> fundingHandler = createFuturesFundingHandler(ex);
				Consumer<MarkPricePatch> markHandler = createFuturesMarkHandler(ex);

				dataStream.subscribeFuturesBookTicker(ex, supportedOnFutures, bookHandler);
				dataStream.subscribeFuturesFundingRates(ex, supportedOnFutures, fundingHandler);
				dataStream.subscribeFuturesMarkPrice(ex, supportedOnFutures, markHandler);
			}

			if (!supportedOnSpot.isEmpty()) {
				Consumer<BookTickerPatch> spotBookHandler = createSpotBookHandler(ex);
				dataStream.subscribeSpotBookTicker(ex, supportedOnSpot, spotBookHandler);
			}
		}

		log.debug("Subscribed to data");
	}

	Consumer<BookTickerPatch> createSpotBookHandler(BaseExchange ex) {
		return tickerPatch -> spotBookTickers.compute(
						ex, tickerPatch.coin(), (coin, v) -> {
							if (v == null) return null;
							BookTicker result = new BookTicker(
											tickerPatch.bidPrice() != null ? tickerPatch.bidPrice() : v.bidPrice(),
											tickerPatch.bidSize() != null ? tickerPatch.bidSize() : v.bidSize(),
											tickerPatch.askPrice() != null ? tickerPatch.askPrice() : v.askPrice(),
											tickerPatch.askSize() != null ? tickerPatch.askSize() : v.askSize(),
											tickerPatch.timestamp()
							);
							completionAgent.processSpotBookTickerUpdate(ex, coin, result);
							return result;
						}
		);
	}

	Consumer<BookTickerPatch> createFuturesTickerHandler(BaseExchange ex) {
		return tickerPatch -> futuresBookTickers.compute(
						ex, tickerPatch.coin(), (k, v) -> {
							if (v == null) return null;
							BookTicker result = new BookTicker(
											tickerPatch.bidPrice() != null ? tickerPatch.bidPrice() : v.bidPrice(),
											tickerPatch.bidSize() != null ? tickerPatch.bidSize() : v.bidSize(),
											tickerPatch.askPrice() != null ? tickerPatch.askPrice() : v.askPrice(),
											tickerPatch.askSize() != null ? tickerPatch.askSize() : v.askSize(),
											tickerPatch.timestamp()
							);
							completionAgent.processFuturesBookTickerUpdate(ex, tickerPatch.coin(), result);
							return result;
						}
		);
	}

	Consumer<FundingRatePatch> createFuturesFundingHandler(BaseExchange ex) {
		return ratePatch -> futuresFundingRates.compute(
						ex, ratePatch.coin(), (k, v) -> {
							if (v == null) return null;
							Funding result = new Funding(
											ratePatch.rate() != null ? ratePatch.rate() : v.rate(),
											ratePatch.settlement() != null ? ratePatch.settlement() : v.settlement(),
											ratePatch.timestamp()
							);
							completionAgent.processFuturesFundingUpdate(ex, ratePatch.coin(), result);
							return result;
						}
		);
	}

	Consumer<MarkPricePatch> createFuturesMarkHandler(BaseExchange ex) {
		return markPricePatch -> futuresMarkPrices.compute(
						ex, markPricePatch.coin(), (k, v) -> {
							if (v == null) return null;
							Mark result = new Mark(markPricePatch.price(), markPricePatch.timestamp());
							completionAgent.processFuturesMarkUpdate(ex, markPricePatch.coin(), result);
							return result;
						}
		);
	}

	void unsubscribeFuturesEntries(List<ExchangeCoinPair> toUnsubscribe) {
		Map<BaseExchange, Set<String>> unsubByExchange = new HashMap<>();
		for (var entry : toUnsubscribe)
			unsubByExchange.computeIfAbsent(entry.ex(), k -> new HashSet<>()).add(entry.coin());

		for (Map.Entry<BaseExchange, Set<String>> entry : unsubByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();
			Set<String> coins = entry.getValue();

			dataStream.unsubscribeFutures(ex, coins);
			removeFuturesFromState(ex, coins);
			completionAgent.removeFutures(ex, coins);
		}
	}

	void unsubscribeSpotEntries(List<ExchangeCoinPair> toUnsubscribe) {
		Map<BaseExchange, Set<String>> unsubByExchange = new HashMap<>();
		for (var entry : toUnsubscribe)
			unsubByExchange.computeIfAbsent(entry.ex(), k -> new HashSet<>()).add(entry.coin());

		for (Map.Entry<BaseExchange, Set<String>> entry : unsubByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();
			Set<String> coins = entry.getValue();

			dataStream.unsubscribeSpot(ex, coins);
			removeSpotFromState(ex, coins);
			completionAgent.removeSpot(ex, coins);
		}
	}

	void unsubscribeAllEntries(List<ExchangeCoinPair> toUnsubscribe) {
		Map<BaseExchange, Set<String>> unsubByExchange = new HashMap<>();
		for (var entry : toUnsubscribe)
			unsubByExchange.computeIfAbsent(entry.ex(), k -> new HashSet<>()).add(entry.coin());

		for (Map.Entry<BaseExchange, Set<String>> entry : unsubByExchange.entrySet()) {
			BaseExchange ex = entry.getKey();
			Set<String> coins = entry.getValue();

			dataStream.unsubscribeAll(ex, coins);
			removeFuturesFromState(ex, coins);
			removeSpotFromState(ex, coins);
			completionAgent.removeCoins(ex, coins);
			removeAvailability(ex, coins);
		}
	}

	void removeAvailability(BaseExchange ex, Set<String> coins) {
		Set<String> exchangeCoins = coinAvailability.getCoins(ex);
		if (exchangeCoins != null) {
			exchangeCoins.removeAll(coins);
			if (exchangeCoins.isEmpty()) coinAvailability.removeByExchange(ex);
		}

		for (String coin : coins) {
			Set<BaseExchange> exchanges = coinAvailability.getExchanges(coin);
			if (exchanges != null) {
				exchanges.remove(ex);
				if (exchanges.isEmpty()) coinAvailability.removeByCoin(coin);
			}
		}
	}

	void removeFuturesFromState(BaseExchange ex, Set<String> coins) {
		futuresFundingRates.removeAll(ex, coins);
		futuresBookTickers.removeAll(ex, coins);
		futuresMarkPrices.removeAll(ex, coins);
		coinAvailability.removeSupportFutures(ex, coins);
	}

	void removeSpotFromState(BaseExchange ex, Set<String> coins) {
		spotBookTickers.removeAll(ex, coins);
		coinAvailability.removeSupportSpot(ex, coins);
	}

	public void shutdown() {
		for (BaseExchange exchange : coinAvailability.getExchanges()) exchange.publicWsClient().close();
		completionAgent.shutdown();
	}

	public FuturesSnapshot getFuturesSnapshot(BaseExchange ex, String coin) {
		BookTicker ticker = futuresBookTickers.get(ex, coin);
		Mark markPrice = futuresMarkPrices.get(ex, coin);
		Funding fundingRate = futuresFundingRates.get(ex, coin);

		return new FuturesSnapshot(ticker, fundingRate, markPrice);
	}

	public SpotSnapshot getSpotSnapshot(BaseExchange ex, String coin) {
		BookTicker ticker = spotBookTickers.get(ex, coin);
		return new SpotSnapshot(ticker);
	}

	public Snapshot getSnapshot(BaseExchange ex, String coin, TradeMarket market) {
		if (market == TradeMarket.FUTURES) return getFuturesSnapshot(ex, coin);
		else return getSpotSnapshot(ex, coin);
	}

	record ExchangeCoinPair(BaseExchange ex, String coin) {
	}
}
