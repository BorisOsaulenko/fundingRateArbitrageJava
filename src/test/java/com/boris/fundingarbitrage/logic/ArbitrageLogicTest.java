package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.FakeExchanges;
import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.balanceprovider.IBalanceProvider;
import com.boris.fundingarbitrage.logic.balancespolicy.IBalancesPolicy;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.NonNull;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ArbitrageLogicTest {
	private final CoinMonitor mockedMonitor = Mockito.mock(CoinMonitor.class);
	private final IOpportunityAnalyzer mockedOpportunityAnalyzer = Mockito.mock(IOpportunityAnalyzer.class);
	private final PreTradeStrategy mockedPreTradeStrategy = Mockito.mock(PreTradeStrategy.class);
	private final InTradeStrategyFactory mockedInTradeStrategyFactory = Mockito.mock(InTradeStrategyFactory.class);
	private final
	private final ArbitrageLogic arbitrageLogic;

	public ArbitrageLogicTest() {
		this.arbitrageLogic = new ArbitrageLogic(
						mockedMonitor,
						mockedOpportunityAnalyzer,
						mockedPreTradeStrategy,
						mockedInTradeStrategyFactory
		) {
			@Override
			protected void processTick(@NonNull CoinVector<CoinOpportunity> bestOpportunities) {
			}

			@Override
			protected void afterBalancesLoaded(@NonNull Map<BaseExchange, ExchangeBalance> balanceMap) {
			}
		};
	}

	private IBalanceProvider mockBalanceProvider() {
		return () -> CompletableFuture.completedFuture(Map.of(
						FakeExchanges.exchange1(), Mockito.mock(),
						FakeExchanges.exchange2(), Mockito.mock(),
						FakeExchanges.exchange3(), Mockito.mock()
		));
	}

	private IBalancesPolicy mockBalancesPolicy() {
		return balances -> {
		};
	}

	private IOpportunityAnalyzer mockOpportunityAnalyzer() {
		return Mockito.mock(IOpportunityAnalyzer.class);
	}

	private CoinAvailabilityRecord mockCoinAvailabilityRecord() {
		var availability = new CoinAvailabilityRecord();
		availability.
	}
}
