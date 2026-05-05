package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TimestampCompletionsSchedulerTest {
	private static final String COIN = "BTC";

	private static TestState createState() {
		ExchangeCoinMap<Funding> futuresFundingRates = new ExchangeCoinMap<>();
		ExchangeCoinMap<BookTicker> futuresBookTickers = new ExchangeCoinMap<>();
		ExchangeCoinMap<Mark> futuresMarkPrices = new ExchangeCoinMap<>();
		ExchangeCoinMap<BookTicker> spotBookTickers = new ExchangeCoinMap<>();
		return new TestState(
						futuresFundingRates,
						futuresBookTickers,
						futuresMarkPrices,
						spotBookTickers,
						new TimestampCompletionsScheduler(
										futuresFundingRates,
										futuresBookTickers,
										futuresMarkPrices,
										spotBookTickers
						)
		);
	}

	private static void fillState(TestState state, BaseExchange exchange, String coin) {
		state.futuresFundingRates().put(
						exchange,
						coin,
						new Funding(new BigDecimal("0.00042"), Instant.now().plusSeconds(1800), Instant.now())
		);
		state.futuresBookTickers().put(
						exchange,
						coin,
						new BookTicker(
										new BigDecimal("120.45"),
										new BigDecimal("4.2"),
										new BigDecimal("120.60"),
										new BigDecimal("3.8"),
										Instant.now()
						)
		);
		state.futuresMarkPrices().put(exchange, coin, new Mark(new BigDecimal("120.52"), Instant.now()));
		state.spotBookTickers().put(
						exchange,
						coin,
						new BookTicker(
										new BigDecimal("119.95"),
										new BigDecimal("5.1"),
										new BigDecimal("120.05"),
										new BigDecimal("4.6"),
										Instant.now()
						)
		);
	}

	private static BaseExchange testExchange() {
		return new BaseExchange(
						ExchangeName.BINANCE,
						mock(PublicWsClient.class),
						mock(PrivateWsClient.class),
						mock(PublicHttpClient.class),
						mock(PrivateHttpClient.class)
		);
	}

	@Test
	void performOnTimestamp_usesUpdatedMarkPriceInCallback() throws Exception {
		TestState state = createState();
		TimestampCompletionsScheduler scheduler = state.scheduler();
		try {
			BaseExchange exchange = testExchange();
			fillState(state, exchange, COIN);

			long timestamp = Instant.now().plusSeconds(3).toEpochMilli();
			Mark updatedMark = new Mark(new BigDecimal("321.123"), Instant.now().plusSeconds(1));
			CountDownLatch callbackLatch = new CountDownLatch(1);
			AtomicReference<FuturesSnapshot> futuresSnapshotRef = new AtomicReference<>();
			AtomicReference<SpotSnapshot> spotSnapshotRef = new AtomicReference<>();

			scheduler.performOnTimestamp(
							timestamp, exchange, COIN, (futures, spot) -> {
								futuresSnapshotRef.set(futures);
								spotSnapshotRef.set(spot);
								callbackLatch.countDown();
							}
			);

			CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
							.execute(() -> {
								scheduler.processFuturesMarkUpdate(exchange, COIN, updatedMark);
								state.futuresMarkPrices().put(exchange, COIN, updatedMark);
							});

			assertTrue(callbackLatch.await(4, TimeUnit.SECONDS));
			assertNotNull(futuresSnapshotRef.get());
			assertNotNull(spotSnapshotRef.get());
			assertEquals(updatedMark, futuresSnapshotRef.get().mark());
		} finally {
			scheduler.shutdown();
		}
	}

	@Test
	void cancelTimestampExecution_preventsCallbackExecution() throws Exception {
		TestState state = createState();
		TimestampCompletionsScheduler scheduler = state.scheduler();
		try {
			BaseExchange exchange = testExchange();
			fillState(state, exchange, COIN);

			long timestamp = Instant.now().plusSeconds(3).toEpochMilli();
			CountDownLatch callbackLatch = new CountDownLatch(1);

			scheduler.performOnTimestamp(timestamp, exchange, COIN, (_, _) -> callbackLatch.countDown());

			CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
							.execute(() -> scheduler.cancelTimestampExecution(exchange, COIN, timestamp));

			assertFalse(callbackLatch.await(4, TimeUnit.SECONDS));
		} finally {
			scheduler.shutdown();
		}
	}

	private record TestState(
					ExchangeCoinMap<Funding> futuresFundingRates,
					ExchangeCoinMap<BookTicker> futuresBookTickers,
					ExchangeCoinMap<Mark> futuresMarkPrices,
					ExchangeCoinMap<BookTicker> spotBookTickers,
					TimestampCompletionsScheduler scheduler
	) {
	}
}
