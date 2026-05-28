package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.FakeExchanges;
import com.boris.fundingarbitrage.FakeModifiableScheduler;
import com.boris.fundingarbitrage.FakeModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.execution.ITradeExecution;
import com.boris.fundingarbitrage.execution.TradeIds;
import com.boris.fundingarbitrage.execution.factory.TradeExecutionFactory;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.monitor.ICoinMonitor;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.tradelogger.TradeSessionLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TradeSessionTest {
	private static final String COIN = "BTC";
	private static final TradeIds ENTER_IDS = new TradeIds("enter-long", "enter-short");
	private static final TradeIds EXIT_IDS = new TradeIds("exit-long", "exit-short");
	private static final FuturesSnapshot LONG_ENTER_SNAPSHOT = snapshot(101.0, 102.0, 103.0, 0.001);
	private static final FuturesSnapshot SHORT_ENTER_SNAPSHOT = snapshot(201.0, 202.0, 203.0, 0.001);
	private static final FuturesSnapshot LONG_EXIT_SNAPSHOT = snapshot(111.0, 112.0, 113.0, 0.001);
	private static final FuturesSnapshot SHORT_EXIT_SNAPSHOT = snapshot(211.0, 212.0, 213.0, 0.001);

	private final ICoinMonitor monitor = mock(ICoinMonitor.class);
	private final IModifiableSchedulerBuilder schedulerBuilder = new FakeModifiableSchedulerBuilder();

	private static FuturesSnapshot snapshot(double bid, double ask, double markPrice, double fundingRate) {
		return new FuturesSnapshot(
						new BookTicker(
										BigDecimal.valueOf(bid),
										BigDecimal.ONE,
										BigDecimal.valueOf(ask),
										BigDecimal.ONE,
										Instant.ofEpochSecond(1)
						),
						new Funding(
										BigDecimal.valueOf(fundingRate),
										Instant.ofEpochSecond(2),
										Instant.ofEpochSecond(3)
						),
						new Mark(BigDecimal.valueOf(markPrice), Instant.ofEpochSecond(4))
		);
	}

	@BeforeEach
	void setUp() {
		FakeModifiableSchedulerBuilder.refresh();
		reset(monitor);
		reset(FakeExchanges.exchange1, FakeExchanges.exchange2, FakeExchanges.exchange3);
		stubExchange(FakeExchanges.exchange1, mock(PrivateHttpClient.class));
		stubExchange(FakeExchanges.exchange2, mock(PrivateHttpClient.class));
	}

	@Test
	void executionWorksFineAndReturnsNormalFutures() {
		Fixture fixture = createFixture(false, false, false);

		CompletableFuture<Void> enterFuture = fixture.session.enter(null);
		assertDoesNotThrow(enterFuture::join);
		assertTrue(enterFuture.isDone());
		assertFalse(enterFuture.isCompletedExceptionally());
		assertTrue(FakeModifiableSchedulerBuilder.allInstancesStarted());

		CompletableFuture<Void> exitFuture = fixture.session.exitTradeIfShould(() -> {
		});
		assertDoesNotThrow(exitFuture::join);
		assertTrue(exitFuture.isDone());
		assertFalse(exitFuture.isCompletedExceptionally());

		FakeModifiableScheduler scheduler = FakeModifiableSchedulerBuilder.getCreatedInstances().get(0);
		assertTrue(scheduler.getHistory().stream().anyMatch(item -> item instanceof FakeModifiableScheduler.Start));
		assertTrue(scheduler.getHistory().stream().anyMatch(item -> item instanceof FakeModifiableScheduler.Stop));
	}

	@Test
	void executionThrowsOnEnterError() {
		Fixture fixture = createFixture(true, false, false);
		Consumer<Throwable> onError = mock(Consumer.class);

		CompletableFuture<Void> enterFuture = fixture.session.enter(onError);
		assertThrows(CompletionException.class, enterFuture::join);
		assertTrue(enterFuture.isCompletedExceptionally());
		verify(onError).accept(any());
		assertFalse(FakeModifiableSchedulerBuilder.allInstancesStarted());
	}

	@Test
	void executionFineOnEnterButThrowsOnExit() {
		Fixture fixture = createFixture(false, true, false);

		CompletableFuture<Void> enterFuture = fixture.session.enter(null);
		assertDoesNotThrow(enterFuture::join);
		assertTrue(FakeModifiableSchedulerBuilder.allInstancesStarted());

		CompletableFuture<Void> exitFuture = fixture.session.exitTradeIfShould(() -> {
		});
		assertThrows(CompletionException.class, exitFuture::join);
		assertTrue(exitFuture.isCompletedExceptionally());

		FakeModifiableScheduler scheduler = FakeModifiableSchedulerBuilder.getCreatedInstances().get(0);
		assertTrue(scheduler.getHistory().stream().anyMatch(item -> item instanceof FakeModifiableScheduler.Start));
		assertTrue(scheduler.getHistory().stream().noneMatch(item -> item instanceof FakeModifiableScheduler.Stop));
	}

	@Test
	void exitWithoutEnterReturnsNullAndSecondAttemptsThrow() {
		Fixture fixture = createFixture(false, false, true);

		assertNull(fixture.session.exitTradeIfShould(() -> {
		}));

		CompletableFuture<Void> enterFuture = fixture.session.enter(null);
		assertDoesNotThrow(enterFuture::join);
		assertThrows(IllegalStateException.class, () -> fixture.session.enter(null));

		CompletableFuture<Void> firstExitFuture = fixture.session.exitTradeIfShould(() -> {
		});
		assertDoesNotThrow(firstExitFuture::join);
		assertThrows(
						IllegalStateException.class, () -> fixture.session.exitTradeIfShould(() -> {
						})
		);
	}

	private Fixture createFixture(boolean enterFails, boolean exitFails, boolean throwOnSecondExit) {
		AtomicReference<TradeSessionLogger> loggerRef = new AtomicReference<>();
		ITradeExecution execution = mock(ITradeExecution.class);
		AtomicInteger exitCalls = new AtomicInteger();
		TradeExecutionFactory executionFactory = mock(TradeExecutionFactory.class);
		when(executionFactory.create(anyString(), any(), any(), any())).thenAnswer(invocation -> {
			loggerRef.set(invocation.getArgument(3));
			return execution;
		});

		when(execution.getEnterIds()).thenReturn(ENTER_IDS);
		when(execution.getExitIds()).thenReturn(EXIT_IDS);
		when(execution.enterTrade()).thenAnswer(invocation -> {
			if (enterFails) return CompletableFuture.failedFuture(new RuntimeException("enter failed"));
			TradeSessionLogger logger = loggerRef.get();
			logger.logEnterSuccess(LONG_ENTER_SNAPSHOT, true);
			logger.logEnterSuccess(SHORT_ENTER_SNAPSHOT, false);
			return CompletableFuture.completedFuture(null);
		});
		when(execution.exitTrade(any(), any())).thenAnswer(invocation -> {
			if (exitFails) return CompletableFuture.failedFuture(new RuntimeException("exit failed"));
			if (throwOnSecondExit && exitCalls.getAndIncrement() > 0) {
				throw new IllegalStateException("exit already used");
			}
			TradeSessionLogger logger = loggerRef.get();
			Snapshot longSnapshot = invocation.getArgument(0);
			Snapshot shortSnapshot = invocation.getArgument(1);
			logger.logExitSuccess(longSnapshot, true);
			logger.logExitSuccess(shortSnapshot, false);
			return CompletableFuture.completedFuture(null);
		});

		when(monitor.getSnapshot(FakeExchanges.exchange1, COIN, TradeMarket.FUTURES)).thenReturn(LONG_EXIT_SNAPSHOT);
		when(monitor.getSnapshot(FakeExchanges.exchange2, COIN, TradeMarket.FUTURES)).thenReturn(SHORT_EXIT_SNAPSHOT);

		return new Fixture(
						new TradeSession(
										COIN,
										createOpportunity(),
										createConfig(),
										monitor,
										createStrategy(),
										executionFactory,
										schedulerBuilder
						)
		);
	}

	private CoinOpportunity createOpportunity() {
		FuturesConstantData constantData = new FuturesConstantData(
						BigDecimal.ONE,
						Fees.allZero(),
						8
		);
		return new CoinOpportunity(
						new ExchangePair(FakeExchanges.exchange1, FakeExchanges.exchange2),
						BigDecimal.ONE,
						ExchangeData.of(constantData, LONG_ENTER_SNAPSHOT),
						ExchangeData.of(constantData, SHORT_ENTER_SNAPSHOT),
						true,
						new TradeDirections(TradeMarket.FUTURES, TradeMarket.FUTURES)
		);
	}

	private ArbitrageBotConfig createConfig() {
		return new ArbitrageBotConfig(
						BigDecimal.ONE,
						BigDecimal.ONE,
						1,
						0,
						1
		);
	}

	private InTradeStrategy createStrategy() {
		return new InTradeStrategy() {
			@Override
			protected void accountForFundingEvent(FuturesSnapshot sn, boolean isLong) {
			}

			@Override
			public boolean shouldExitTrade(Snapshot longCurrent, Snapshot shortCurrent) {
				return true;
			}
		};
	}

	private void stubExchange(BaseExchange exchange, PrivateHttpClient privateHttpClient) {
		when(exchange.privateHttpClient()).thenReturn(privateHttpClient);
		when(privateHttpClient.getFuturesOrderRecord(anyString(), anyString(), any()))
						.thenReturn(CompletableFuture.completedFuture(List.of()));
		when(privateHttpClient.getSpotOrderRecord(anyString(), anyString(), any()))
						.thenReturn(CompletableFuture.completedFuture(List.of()));
	}

	private record Fixture(TradeSession session) {
	}
}
