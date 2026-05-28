package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.mocks.*;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.exchangedata.ExchangeData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class TradeSessionTest {
	private static final String COIN = "BTC";
	private static final FuturesSnapshot LONG_ENTER_SNAPSHOT = snapshot(101.0, 102.0, 103.0, 0.001);
	private static final FuturesSnapshot SHORT_ENTER_SNAPSHOT = snapshot(201.0, 202.0, 203.0, 0.001);
	private static final FuturesSnapshot LONG_EXIT_SNAPSHOT = snapshot(111.0, 112.0, 113.0, 0.001);
	private static final FuturesSnapshot SHORT_EXIT_SNAPSHOT = snapshot(211.0, 212.0, 213.0, 0.001);

	private final FakeCoinMonitor monitor = new FakeCoinMonitor();
	private final FakeModifiableSchedulerBuilder modifiableSchedulerBuilder = new FakeModifiableSchedulerBuilder();
	private final FakeOneTimeSchedulerSupplier oneTimeSchedulerSupplier = new FakeOneTimeSchedulerSupplier();
	private final FakeTradeSessionLoggerBuilder tradeLoggerBuilder = new FakeTradeSessionLoggerBuilder();
	private final FakeInTradeStrategy strategy = new FakeInTradeStrategy();

	private FakeTradeExecution execution;
	private TradeSession session;

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
		strategy.setShouldExitTrade(true);
		FakeModifiableSchedulerBuilder.refresh();
		FakeOneTimeSchedulerSupplier.refresh();
		monitor.reset();

		monitor.setFuturesSnapshot(FakeExchanges.exchange1, COIN, LONG_EXIT_SNAPSHOT);
		monitor.setFuturesSnapshot(FakeExchanges.exchange2, COIN, SHORT_EXIT_SNAPSHOT);

		execution = new FakeTradeExecution();
		session = new TradeSession(
						COIN,
						createOpportunity(),
						createConfig(),
						monitor,
						strategy,
						FakeTradeExecution.factory(execution),
						modifiableSchedulerBuilder,
						oneTimeSchedulerSupplier,
						tradeLoggerBuilder
		);
	}

	@Test
	void executionWorksFineAndReturnsNormalFutures() {
		CompletableFuture<Void> enterFuture = session.enter(null);
		assertDoesNotThrow(enterFuture::join);
		assertTrue(enterFuture.isDone());
		assertFalse(enterFuture.isCompletedExceptionally());
		assertTrue(FakeModifiableSchedulerBuilder.allInstancesStarted());

		CompletableFuture<Void> exitFuture = session.exitTradeIfShould(null);
		assertDoesNotThrow(exitFuture::join);
		assertTrue(exitFuture.isDone());
		assertFalse(exitFuture.isCompletedExceptionally());
		FakeOneTimeSchedulerSupplier.getCreatedInstances().forEach(FakeOneTimeScheduler::doRunAll);

		FakeModifiableScheduler scheduler = FakeModifiableSchedulerBuilder.getCreatedInstances().getFirst();
		assertTrue(scheduler.getHistory().stream().anyMatch(item -> item instanceof FakeModifiableScheduler.Start));
		assertTrue(scheduler.getHistory().stream().anyMatch(item -> item instanceof FakeModifiableScheduler.Stop));
		assertTrue(FakeOneTimeSchedulerSupplier.allInstancesShutdown());
	}

	@Test
	void fundingRegistrationRegistersFundingInStrategy() {
		CompletableFuture<Void> enterFuture = session.enter(null);
		assertDoesNotThrow(enterFuture::join);
		assertTrue(enterFuture.isDone());
		assertFalse(enterFuture.isCompletedExceptionally());

		FakeModifiableSchedulerBuilder.getCreatedInstances().getFirst().doRun();

		assertEquals(1, strategy.getLongFundingSnapshots().size());
		assertEquals(1, strategy.getShortFundingSnapshots().size());
		assertEquals(LONG_EXIT_SNAPSHOT, strategy.getLongFundingSnapshots().getFirst());
		assertEquals(SHORT_EXIT_SNAPSHOT, strategy.getShortFundingSnapshots().getFirst());
	}

	@Test
	void executionThrowsOnEnterError() {
		execution.enterFails();

		CompletableFuture<Void> enterFuture = session.enter(null);
		assertThrows(CompletionException.class, enterFuture::join);
		assertTrue(enterFuture.isCompletedExceptionally());
		assertNull(execution.getEnterIds());
		assertNull(execution.getExitIds());
		assertFalse(FakeModifiableSchedulerBuilder.allInstancesStarted());
	}

	@Test
	void executionFineOnEnterButThrowsOnExit() {
		execution.exitFails();

		CompletableFuture<Void> enterFuture = session.enter(null);
		assertDoesNotThrow(enterFuture::join);
		assertTrue(FakeModifiableSchedulerBuilder.allInstancesStarted());

		CompletableFuture<Void> exitFuture = session.exitTradeIfShould(null);
		assertThrows(CompletionException.class, exitFuture::join);
		assertTrue(exitFuture.isCompletedExceptionally());
		assertNull(execution.getExitIds());

		FakeModifiableScheduler scheduler = FakeModifiableSchedulerBuilder.getCreatedInstances().get(0);
		assertTrue(scheduler.getHistory().stream().anyMatch(item -> item instanceof FakeModifiableScheduler.Start));
		assertTrue(scheduler.getHistory().stream().noneMatch(item -> item instanceof FakeModifiableScheduler.Stop));
	}

	@Test
	void exitWithoutEnterThrows() {
		assertThrows(IllegalStateException.class, () -> session.exitTradeIfShould(null));
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
}
