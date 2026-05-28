package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.scheduler.OneTimeScheduler;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;

class TimestampCompletionsScheduler {
	private static final long COMPLETION_DELAY_MS = 500;
	private final ExchangeCoinMap<Funding> futuresFundingRates;
	private final ExchangeCoinMap<BookTicker> futuresBookTickers;
	private final ExchangeCoinMap<Mark> futuresMarkPrices;
	private final ExchangeCoinMap<BookTicker> spotBookTickers;
	private final ExchangeCoinMap<SortedSet<Long>> timestampsToProcess = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresTickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Funding> futuresFundingCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Mark> futuresMarkCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickerCompletions = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Map<Long, Set<BiConsumer<FuturesSnapshot, SpotSnapshot>>>> timestampHandlers = new ExchangeCoinMap<>();
	private final OneTimeScheduler completionScheduler;

	TimestampCompletionsScheduler(
					ExchangeCoinMap<Funding> futuresFundingRates,
					ExchangeCoinMap<BookTicker> futuresBookTickers,
					ExchangeCoinMap<Mark> futuresMarkPrices,
					ExchangeCoinMap<BookTicker> spotBookTickers,
					OneTimeScheduler scheduler
	) {
		this.futuresFundingRates = futuresFundingRates;
		this.futuresBookTickers = futuresBookTickers;
		this.futuresMarkPrices = futuresMarkPrices;
		this.spotBookTickers = spotBookTickers;
		this.completionScheduler = scheduler;
	}

	public TimestampCompletionsScheduler(
					ExchangeCoinMap<Funding> futuresFundingRates,
					ExchangeCoinMap<BookTicker> futuresBookTickers,
					ExchangeCoinMap<Mark> futuresMarkPrices,
					ExchangeCoinMap<BookTicker> spotBookTickers
	) {
		this(futuresFundingRates, futuresBookTickers, futuresMarkPrices, spotBookTickers, new OneTimeScheduler());
	}

	public void performOnTimestamp(
					long timestamp,
					BaseExchange exchange,
					String coin,
					BiConsumer<FuturesSnapshot, SpotSnapshot> handler
	) {
		long now = Instant.now().toEpochMilli();
		long duration = timestamp - now;

		if (duration < 0) throw new IllegalArgumentException("Timestamp is in the past: " + timestamp);

		registerTimestampAndHandler(timestamp, exchange, coin, handler);
		registerCurrentState(exchange, coin);

		completionScheduler.schedule(
						() -> fireCallbacksOnTimestamp(timestamp, exchange, coin),
						duration + COMPLETION_DELAY_MS
		);
	}

	public void cancelTimestampExecution(BaseExchange ex, String coin, long timestamp) {
		timestampHandlers.consumeIfPresent(ex, coin, (_, timestampMap) -> timestampMap.remove(timestamp));
		timestampsToProcess.consumeIfPresent(ex, coin, (_, timestamps) -> timestamps.remove(timestamp));
	}

	void registerCurrentState(BaseExchange ex, String coin) {
		futuresFundingCompletions.put(ex, coin, futuresFundingRates.get(ex, coin));
		futuresTickerCompletions.put(ex, coin, futuresBookTickers.get(ex, coin));
		futuresMarkCompletions.put(ex, coin, futuresMarkPrices.get(ex, coin));
		spotBookTickerCompletions.put(ex, coin, spotBookTickers.get(ex, coin));
	}

	void registerTimestampAndHandler(
					long timestamp,
					BaseExchange ex,
					String coin,
					BiConsumer<FuturesSnapshot, SpotSnapshot> handler
	) {
		timestampsToProcess
						.computeIfAbsent(ex, coin, _ -> new ConcurrentSkipListSet<>())
						.add(timestamp);

		timestampHandlers
						.computeIfAbsent(ex, coin, _ -> new ConcurrentHashMap<>())
						.computeIfAbsent(timestamp, _ -> ConcurrentHashMap.newKeySet()).add(handler);
	}

	void fireCallbacksOnTimestamp(long timestamp, BaseExchange ex, String coin) {
		Set<BiConsumer<FuturesSnapshot, SpotSnapshot>> handlers = timestampHandlers.get(ex, coin).get(timestamp);
		if (handlers == null) return;

		FuturesSnapshot futuresTimestampSn = getTimestampFuturesCompletion(ex, coin);
		SpotSnapshot spotTimestampSn = getTimestampSpotCompletion(ex, coin);
		handlers.forEach(handler -> handler.accept(futuresTimestampSn, spotTimestampSn));
		timestampsToProcess.get(ex, coin).remove(timestamp);
		timestampHandlers.get(ex, coin).remove(timestamp);
	}

	FuturesSnapshot getTimestampFuturesCompletion(BaseExchange ex, String coin) {
		BookTicker ticker = futuresTickerCompletions.get(ex, coin);
		Mark markPrice = futuresMarkCompletions.get(ex, coin);
		Funding fundingRate = futuresFundingCompletions.get(ex, coin);
		return new FuturesSnapshot(ticker, fundingRate, markPrice);
	}

	SpotSnapshot getTimestampSpotCompletion(BaseExchange ex, String coin) {
		BookTicker ticker = spotBookTickerCompletions.get(ex, coin);
		return new SpotSnapshot(ticker);
	}

	Long getEarliestTimestamp(BaseExchange ex, String coin) {
		SortedSet<Long> timestamps = timestampsToProcess.get(ex, coin);
		if (timestamps == null || timestamps.isEmpty()) return null;
		return timestamps.first();
	}

	void processSpotBookTickerUpdate(BaseExchange ex, String coin, BookTicker bookTicker) {
		Long timestamp = getEarliestTimestamp(ex, coin);
		if (timestamp == null) return;
		spotBookTickerCompletions.put(ex, coin, bookTicker);
	}

	void processFuturesBookTickerUpdate(BaseExchange ex, String coin, BookTicker bookTicker) {
		Long timestamp = getEarliestTimestamp(ex, coin);
		if (timestamp == null) return;
		futuresTickerCompletions.put(ex, coin, bookTicker);
	}

	void processFuturesFundingUpdate(BaseExchange ex, String coin, Funding funding) {
		Long timestamp = getEarliestTimestamp(ex, coin);
		if (timestamp == null) return;
		futuresFundingCompletions.put(ex, coin, funding);
	}

	void processFuturesMarkUpdate(BaseExchange ex, String coin, Mark mark) {
		Long timestamp = getEarliestTimestamp(ex, coin);
		if (timestamp == null) return;
		futuresMarkCompletions.put(ex, coin, mark);
	}

	void removeFutures(BaseExchange ex, Set<String> coin) {
		futuresTickerCompletions.removeAll(ex, coin);
		futuresFundingCompletions.removeAll(ex, coin);
		futuresMarkCompletions.removeAll(ex, coin);
	}

	void removeSpot(BaseExchange ex, Set<String> coin) {
		spotBookTickerCompletions.removeAll(ex, coin);
	}

	void removeCoins(BaseExchange ex, Set<String> coins) {
		timestampsToProcess.removeAll(ex, coins);
		timestampHandlers.removeAll(ex, coins);
		removeFutures(ex, coins);
		removeSpot(ex, coins);
	}

	void shutdown() {
		completionScheduler.shutdown();
	}
}