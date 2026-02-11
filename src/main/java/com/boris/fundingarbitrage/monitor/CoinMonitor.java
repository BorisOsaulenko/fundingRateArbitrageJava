package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CoinMonitor {
	private final Collection<String> coins;
	private final int waitForDataSecond = 40;
	private final ExchangeCoinMap<FundingRate> fundingRates = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> bookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Fees> fees = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<MarkPrice> markPrices = new ExchangeCoinMap<>();
	private final ConcurrentHashMap<String, AtomicInteger> inflight = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, AtomicInteger> maxInflight = new ConcurrentHashMap<>();
	@Getter
	private final CompletableFuture<Void> initFuture;

	public CoinMonitor(Collection<String> coins) {
		this.coins = coins;

		initFuture = CompletableFuture.runAsync(() -> {
			initExchanges();
			subscribeBookTickers();
			subscribeFundingRates();
			subscribeMarkPrices();

			try {
				Thread.sleep(waitForDataSecond * 1000L);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			Logger.log(maxInflight.values().toString());

			String dataCompletMessage = checkDataCompleteness();
			if (dataCompletMessage != null) {
				Logger.error(dataCompletMessage);
				throw new RuntimeException(dataCompletMessage);
			}
		});
	}

	private String checkDataCompleteness() {
		for (Map.Entry<String, BookTicker> entry : bookTickers.entrySet()) {
			if (entry.getValue().bidPrice == 0 ||
					entry.getValue().bidSize == 0 ||
					entry.getValue().askPrice == 0 ||
					entry.getValue().askSize == 0 ||
					entry.getValue().timestamp == Instant.EPOCH) {
				return "Book ticker data is incomplete for " + entry.getKey();
			}
		}

		for (Map.Entry<String, FundingRate> entry : fundingRates.entrySet()) {
			if (entry.getValue().rate == 0 ||
					entry.getValue().settlement == null ||
					entry.getValue().settlement == Instant.EPOCH) {
				return "Funding rate data is incomplete for " + entry.getKey();
			}
		}

		for (Map.Entry<String, MarkPrice> entry : markPrices.entrySet()) {
			if (entry.getValue().price == 0 || entry.getValue().timestamp == Instant.EPOCH) {
				return "Mark price data is incomplete for " + entry.getKey();
			}
		}

		return null;
	}

	private void initExchanges() {
		for (BaseExchange exchange : Instances.getExchangeArray()) {
			for (String coin : coins) {
				fundingRates.put(exchange.name, coin, FundingRate.empty());
				bookTickers.put(exchange.name, coin, BookTicker.empty());
				fees.put(exchange.name, coin, Fees.empty());
				markPrices.put(exchange.name, coin, MarkPrice.empty());
			}
		}
	}

	private void subscribeBookTickers() {
		for (BaseExchange exchange : Instances.getExchangeArray()) {
			exchange.publicWsClient.subscribeBookTicker(
							coins, tickerPatch -> {
								String key = overlapKey(exchange.name.name(), tickerPatch.coin(), "book");
								before(key);
								bookTickers.compute(
												exchange.name, tickerPatch.coin(), (k, v) -> {
													if (tickerPatch.bidSize() != null) v.bidSize = tickerPatch.bidSize();
													if (tickerPatch.bidPrice() != null) v.bidPrice = tickerPatch.bidPrice();
													if (tickerPatch.askPrice() != null) v.askPrice = tickerPatch.askPrice();
													if (tickerPatch.askSize() != null) v.askSize = tickerPatch.askSize();
													v.timestamp = tickerPatch.timestamp();
													return v;
												}
								);
								after(key);
							}
			);
		}
	}


	private void subscribeFundingRates() {
		for (BaseExchange exchange : Instances.getExchangeArray()) {
			exchange.publicWsClient.subscribeFundingRates(
							coins, ratePatch -> {
								String key = overlapKey(exchange.name.name(), ratePatch.coin(), "funding");
								before(key);
								fundingRates.compute(
												exchange.name, ratePatch.coin(), (k, v) -> {
													if (ratePatch.rate() != null) v.rate = ratePatch.rate();
													if (ratePatch.settlement() != null) v.settlement = ratePatch.settlement();
													v.timestamp = ratePatch.timestamp();
													return v;
												}
								);
								after(key);
							}
			);

		}
	}

	private void subscribeMarkPrices() {
		for (BaseExchange exchange : Instances.getExchangeArray()) {
			exchange.publicWsClient.subscribeMarkPrice(
							coins, markPricePatch -> {
								String key = overlapKey(exchange.name.name(), markPricePatch.coin(), "mark");
								before(key);
								markPrices.compute(
												exchange.name, markPricePatch.coin(), (k, v) -> {
													v.price = markPricePatch.price();
													v.timestamp = markPricePatch.timestamp();
													return v;
												}
								);
								after(key);
							}
			);
		}
	}

	private String overlapKey(String exchange, String coin, String stream) {
		return exchange + ":" + coin + ":" + stream;
	}

	private void before(String key) {
		int now = inflight.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
		maxInflight.computeIfAbsent(key, k -> new AtomicInteger()).accumulateAndGet(now, Math::max);
	}

	private void after(String key) {
		AtomicInteger counter = inflight.get(key);
		if (counter != null) counter.decrementAndGet();
	}
}
