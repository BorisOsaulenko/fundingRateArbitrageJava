package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.coinfilter.ConstantDataRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicMarketDataStream;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.boris.fundingarbitrage.strategy.TradeMarket.FUTURES;
import static com.boris.fundingarbitrage.strategy.TradeMarket.SPOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CoinMonitorTest {
	private static final String COIN = "BTC";

	private static CoinFilterResult filterData(Set<BaseExchange> exchanges) {
		CoinAvailabilityRecord support = new CoinAvailabilityRecord();
		ExchangeCoinMap<Boolean> presentOnFutures = new ExchangeCoinMap<>();
		ExchangeCoinMap<Boolean> presentOnSpot = new ExchangeCoinMap<>();

		for (BaseExchange exchange : exchanges) {
			support.exchangesByCoin().computeIfAbsent(COIN, _ -> ConcurrentHashMap.newKeySet()).add(exchange);
			support.coinsByExchange().computeIfAbsent(exchange, _ -> ConcurrentHashMap.newKeySet()).add(COIN);
			presentOnFutures.put(exchange, COIN, true);
			presentOnSpot.put(exchange, COIN, true);
		}

		return new CoinFilterResult(
						support,
						new ConstantDataRecord(),
						new ExchangeCoinMap<>(),
						new ExchangeCoinMap<>()
		);
	}

	private static BaseExchange fakeExchange(ExchangeName name, PublicMarketDataStream publicWsClient) {
		return new BaseExchange(
						name,
						publicWsClient,
						mock(PrivateWsClient.class),
						mock(PublicHttpClient.class),
						mock(PrivateHttpClient.class)
		);
	}

	@Test
	void getSnapshotReturnsLatestValuesFromSubscribedWebsocketPatches() {
		FakePublicMarketDataStream binanceWs = new FakePublicMarketDataStream();
		FakePublicMarketDataStream bybitWs = new FakePublicMarketDataStream();
		BaseExchange binance = fakeExchange(ExchangeName.BINANCE, binanceWs);
		BaseExchange bybit = fakeExchange(ExchangeName.BYBIT, bybitWs);
		CoinMonitor monitor = new CoinMonitor(filterData(Set.of(binance, bybit)), new ImmediateDataStream());

		Instant updatedAt = Instant.parse("2026-05-05T10:15:30Z");
		Instant settlement = Instant.parse("2026-05-05T16:00:00Z");
		binanceWs.emitFuturesBookTicker(new BookTickerPatch(
						COIN,
						new BigDecimal("101.10"),
						new BigDecimal("1.11"),
						new BigDecimal("101.20"),
						new BigDecimal("2.22"),
						updatedAt
		));
		binanceWs.emitFuturesFundingRate(new FundingRatePatch(
						COIN,
						new BigDecimal("0.00042"),
						settlement,
						updatedAt
		));
		binanceWs.emitFuturesMarkPrice(new MarkPricePatch(COIN, new BigDecimal("101.15"), updatedAt));
		binanceWs.emitSpotBookTicker(new BookTickerPatch(
						COIN,
						new BigDecimal("100.90"),
						new BigDecimal("3.33"),
						new BigDecimal("101.00"),
						new BigDecimal("4.44"),
						updatedAt
		));

		Instant bybitUpdatedAt = Instant.parse("2026-05-05T10:16:00Z");
		bybitWs.emitFuturesMarkPrice(new MarkPricePatch(COIN, new BigDecimal("202.25"), bybitUpdatedAt));

		FuturesSnapshot futures = monitor.getFuturesSnapshot(binance, COIN);
		assertEquals(new BigDecimal("101.10"), futures.bookTicker().bidPrice());
		assertEquals(new BigDecimal("1.11"), futures.bookTicker().bidSize());
		assertEquals(new BigDecimal("101.20"), futures.bookTicker().askPrice());
		assertEquals(new BigDecimal("2.22"), futures.bookTicker().askSize());
		assertEquals(updatedAt, futures.bookTicker().timestamp());
		assertEquals(new BigDecimal("0.00042"), futures.funding().rate());
		assertEquals(settlement, futures.funding().settlement());
		assertEquals(updatedAt, futures.funding().timestamp());
		assertEquals(new BigDecimal("101.15"), futures.mark().price());
		assertEquals(updatedAt, futures.mark().timestamp());

		SpotSnapshot spot = monitor.getSpotSnapshot(binance, COIN);
		assertEquals(new BigDecimal("100.90"), spot.bookTicker().bidPrice());
		assertEquals(new BigDecimal("3.33"), spot.bookTicker().bidSize());
		assertEquals(new BigDecimal("101.00"), spot.bookTicker().askPrice());
		assertEquals(new BigDecimal("4.44"), spot.bookTicker().askSize());
		assertEquals(updatedAt, spot.bookTicker().timestamp());

		assertEquals(futures, monitor.getSnapshot(binance, COIN, FUTURES));
		assertEquals(spot, monitor.getSnapshot(binance, COIN, SPOT));
		assertEquals(new BigDecimal("202.25"), monitor.getFuturesSnapshot(bybit, COIN).mark().price());
		assertEquals(bybitUpdatedAt, monitor.getFuturesSnapshot(bybit, COIN).mark().timestamp());
	}

	private static final class ImmediateDataStream implements IDataStream {
		@Override
		public @NonNull CompletableFuture<Void> initFuture() {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public @NonNull CompletableFuture<Void> openWsConnections(Set<BaseExchange> exchanges) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onSteadyData(Runnable onSteadyData) {
			onSteadyData.run();
		}
	}

	private static final class FakePublicMarketDataStream implements PublicMarketDataStream {
		private final ConcurrentHashMap<String, Consumer<BookTickerPatch>> futuresBookTickerHandlers = new ConcurrentHashMap<>();
		private final ConcurrentHashMap<String, Consumer<FundingRatePatch>> futuresFundingHandlers = new ConcurrentHashMap<>();
		private final ConcurrentHashMap<String, Consumer<MarkPricePatch>> futuresMarkHandlers = new ConcurrentHashMap<>();
		private final ConcurrentHashMap<String, Consumer<BookTickerPatch>> spotBookTickerHandlers = new ConcurrentHashMap<>();

		@Override
		public void onUnhandledDisconnect(Runnable hook) {
		}

		@Override
		public CompletableFuture<Void> connect() {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void close() {
		}

		@Override
		public void subscribeFuturesFundingRates(Set<String> coins, Consumer<FundingRatePatch> handler) {
			coins.forEach(coin -> {
				futuresFundingHandlers.put(coin, handler);
				emitFuturesFundingRate(new FundingRatePatch(
								coin,
								new BigDecimal("0.0001"),
								Instant.parse("2026-05-05T08:00:00Z"),
								Instant.parse("2026-05-05T00:00:00Z")
				));
			});
		}

		@Override
		public void subscribeFuturesBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
			coins.forEach(coin -> {
				futuresBookTickerHandlers.put(coin, handler);
				emitFuturesBookTicker(new BookTickerPatch(
								coin,
								new BigDecimal("99.90"),
								new BigDecimal("1"),
								new BigDecimal("100.10"),
								new BigDecimal("1"),
								Instant.parse("2026-05-05T00:00:00Z")
				));
			});
		}

		@Override
		public void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPricePatch> handler) {
			coins.forEach(coin -> {
				futuresMarkHandlers.put(coin, handler);
				emitFuturesMarkPrice(new MarkPricePatch(
								coin,
								new BigDecimal("100"),
								Instant.parse("2026-05-05T00:00:00Z")
				));
			});
		}

		@Override
		public void subscribeSpotBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
			coins.forEach(coin -> {
				spotBookTickerHandlers.put(coin, handler);
				emitSpotBookTicker(new BookTickerPatch(
								coin,
								new BigDecimal("99.80"),
								new BigDecimal("1"),
								new BigDecimal("100.00"),
								new BigDecimal("1"),
								Instant.parse("2026-05-05T00:00:00Z")
				));
			});
		}

		@Override
		public void unsubscribeCoinsFutures(Set<String> coins) {
			coins.forEach(coin -> {
				futuresBookTickerHandlers.remove(coin);
				futuresFundingHandlers.remove(coin);
				futuresMarkHandlers.remove(coin);
			});
		}

		@Override
		public void unsubscribeCoinsSpot(Set<String> coins) {
			coins.forEach(spotBookTickerHandlers::remove);
		}

		private void emitFuturesBookTicker(BookTickerPatch patch) {
			Consumer<BookTickerPatch> handler = futuresBookTickerHandlers.get(patch.coin());
			if (handler != null) handler.accept(patch);
		}

		private void emitFuturesFundingRate(FundingRatePatch patch) {
			Consumer<FundingRatePatch> handler = futuresFundingHandlers.get(patch.coin());
			if (handler != null) handler.accept(patch);
		}

		private void emitFuturesMarkPrice(MarkPricePatch patch) {
			Consumer<MarkPricePatch> handler = futuresMarkHandlers.get(patch.coin());
			if (handler != null) handler.accept(patch);
		}

		private void emitSpotBookTicker(BookTickerPatch patch) {
			Consumer<BookTickerPatch> handler = spotBookTickerHandlers.get(patch.coin());
			if (handler != null) handler.accept(patch);
		}
	}
}
