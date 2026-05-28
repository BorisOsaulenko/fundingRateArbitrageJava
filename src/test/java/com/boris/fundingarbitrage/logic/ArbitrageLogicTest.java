package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.FakeCoinMonitor;
import com.boris.fundingarbitrage.FakeModifiableScheduler;
import com.boris.fundingarbitrage.FakeModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.coinfilter.TestCoinAvailabilityFactory;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.balanceprovider.IBalanceProvider;
import com.boris.fundingarbitrage.logic.balancespolicy.IBalancesPolicy;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ArbitrageLogicTest {
	private final FakeCoinMonitor mockedMonitor = new FakeCoinMonitor();
	private final IOpportunityAnalyzer mockedOpportunityAnalyzer = Mockito.mock(IOpportunityAnalyzer.class);
	private final PreTradeStrategy mockedPreTradeStrategy = Mockito.mock(PreTradeStrategy.class);
	private final IBalanceProvider mockedBalanceProvider = Mockito.mock(IBalanceProvider.class);
	private final IBalancesPolicy mockedBalancesPolicy = Mockito.mock(IBalancesPolicy.class);
	private final ArbitrageBotConfig mockedConfig = Mockito.mock(ArbitrageBotConfig.class);
	private final FakeModifiableSchedulerBuilder mockedSchedulerBuilder = new FakeModifiableSchedulerBuilder();
	private final Map<BaseExchange, ExchangeBalance> fakeBalances = new HashMap<>();
	private final CoinVector<CoinOpportunity> fakeOpportunities = CoinVector.byDefaultValue(
					TestCoinAvailabilityFactory.coinsSet,
					Mockito.mock(CoinOpportunity.class)
	);
	private TestArbitrageLogic logic;

	private void initializeLogicSpy(CoinAvailabilityRecord availability) {
		Mockito.when(mockedBalanceProvider.loadBalances()).thenReturn(CompletableFuture.completedFuture(fakeBalances));
		Mockito.when(mockedOpportunityAnalyzer.processCoins(Mockito.any()))
						.thenReturn(CompletableFuture.completedFuture(fakeOpportunities));
		FakeModifiableSchedulerBuilder.refresh();
		logic = new TestArbitrageLogic(availability);
	}

	@Test
	void emptyCoinAvailabilityShutsDown() {
		initializeLogicSpy(new CoinAvailabilityRecord());
		logic.init(mockedBalanceProvider);
		mockedMonitor.verifyShutdown();
		Mockito.verify(mockedOpportunityAnalyzer).shutdown();
	}

	@Test
	void validCoinAvailabilityWorksAsExpected() {
		initializeLogicSpy(new TestCoinAvailabilityFactory().addFullCoin1Support().addFullCoin2Support().build());
		CompletableFuture<Void> initFuture = logic.init(mockedBalanceProvider);
		assertTrue(initFuture.isDone());
		assertFalse(initFuture.isCompletedExceptionally());
		mockedMonitor.verifyNotShutdown();
		Mockito.verify(mockedOpportunityAnalyzer, Mockito.never()).shutdown();
		Mockito.verify(mockedBalancesPolicy).validateBalancesMap(fakeBalances);
		Mockito.verify(logic.afterBalancesLoadedConsumer).accept(fakeBalances);
	}

	@Test
	void startBeforeInitThrowsException() {
		initializeLogicSpy(new TestCoinAvailabilityFactory().addFullCoin1Support().build());
		assertThrows(IllegalStateException.class, logic::start);
	}

	@Test
	void startStartsOpportunityProcessingExecutor() {
		initializeLogicSpy(new TestCoinAvailabilityFactory().addFullCoin1Support().addFullCoin2Support().build());
		logic.init(mockedBalanceProvider);
		logic.start();
		assertTrue(FakeModifiableSchedulerBuilder.allInstancesStarted());
		FakeModifiableSchedulerBuilder.getCreatedInstances().forEach(FakeModifiableScheduler::doRun);
		Mockito.verify(mockedOpportunityAnalyzer).processCoins(Mockito.any());
	}

	@Test
	void emptyOpportunityAnalyzerResultShutsDown() {
		initializeLogicSpy(new TestCoinAvailabilityFactory().addFullCoin1Support().addFullCoin2Support().build());
		Mockito.when(mockedOpportunityAnalyzer.processCoins(Mockito.any()))
						.thenReturn(CompletableFuture.completedFuture(new CoinVector<>()));
		logic.init(mockedBalanceProvider);
		logic.start();
		FakeModifiableSchedulerBuilder.getCreatedInstances().forEach(FakeModifiableScheduler::doRun);
		Mockito.verify(mockedOpportunityAnalyzer).processCoins(Mockito.any());
		mockedMonitor.verifyShutdown();
		Mockito.verify(mockedOpportunityAnalyzer).shutdown();
	}

	@Test
	void validOpportunityAnalyzerResultWorksAsExpected() {
		initializeLogicSpy(new TestCoinAvailabilityFactory().addFullCoin1Support().addFullCoin2Support().build());
		Mockito.when(mockedOpportunityAnalyzer.processCoins(Mockito.any()))
						.thenReturn(CompletableFuture.completedFuture(fakeOpportunities));
		logic.init(mockedBalanceProvider);
		logic.start();
		FakeModifiableSchedulerBuilder.getCreatedInstances().forEach(FakeModifiableScheduler::doRun);
		Mockito.verify(mockedOpportunityAnalyzer).processCoins(Mockito.any());
		Mockito.verify(logic.processTickConsumer).accept(fakeOpportunities);
	}

	class TestArbitrageLogic extends ArbitrageLogic {
		private final Consumer<CoinVector<CoinOpportunity>> processTickConsumer = Mockito.mock(Consumer.class);
		private final Consumer<Map<BaseExchange, ExchangeBalance>> afterBalancesLoadedConsumer = Mockito.mock(Consumer.class);

		private TestArbitrageLogic(CoinAvailabilityRecord availability) {
			super(
							mockedMonitor,
							mockedOpportunityAnalyzer,
							mockedPreTradeStrategy,
							availability,
							mockedConfig,
							mockedBalancesPolicy,
							mockedSchedulerBuilder
			);
		}

		@Override
		protected void processTick(@NonNull CoinVector<CoinOpportunity> bestOpportunities) {
			processTickConsumer.accept(bestOpportunities);
		}

		@Override
		protected void afterBalancesLoaded(@NonNull Map<BaseExchange, ExchangeBalance> balanceMap) {
			afterBalancesLoadedConsumer.accept(balanceMap);
		}
	}
}
