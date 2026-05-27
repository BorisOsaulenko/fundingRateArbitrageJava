package com.boris.fundingarbitrage.logic.implementations;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.factory.CoinExecutionFactory;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.logic.InTradeCoinLogic;
import com.boris.fundingarbitrage.logic.balancespolicy.IBalancesPolicy;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;
import com.boris.fundingarbitrage.strategy.pretradestrategy.PreTradeStrategy;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class RebalancingArbitrageLogic extends ArbitrageLogic {
	private final static Logger log = LoggerFactory.getLogger(RebalancingArbitrageLogic.class);
	private final CoinExecutionFactory executionFactory;
	private final IModifiableSchedulerBuilder schedulerBuilder;
	private final Set<BaseExchange> usedExchanges = new CopyOnWriteArraySet<>();
	private final Map<String, InTradeCoinLogic> enteredCoins = new ConcurrentHashMap<>();
	private final IModifiableScheduler exitScheduler;
	private final Set<CompletableFuture<Void>> exitFutures = new CopyOnWriteArraySet<>();
	private CompletableFuture<Void> internalTransfersFuture;

	public RebalancingArbitrageLogic(
					CoinMonitor monitor,
					IOpportunityAnalyzer opportunityAnalyzer,
					PreTradeStrategy preTradeStrategy,
					InTradeStrategyFactory inTradeStrategyFactory,
					CoinAvailabilityRecord coinAvailability,
					ArbitrageBotConfig arbConfig,
					IBalancesPolicy balancesPolicy,
					CoinExecutionFactory executionFactory,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(
						monitor,
						opportunityAnalyzer,
						preTradeStrategy,
						inTradeStrategyFactory,
						coinAvailability,
						arbConfig,
						balancesPolicy,
						schedulerBuilder
		);
		this.schedulerBuilder = schedulerBuilder;
		this.executionFactory = executionFactory;
		this.exitScheduler = schedulerBuilder.create(this::processExits, 10);
		this.exitScheduler.start();
	}

	@Override
	public void afterBalancesLoaded(@NotNull Map<BaseExchange, ExchangeBalance> balanceMap) {
		BigDecimal required = config.legUsdtAmount().add(config.safetyMargin());
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (Map.Entry<BaseExchange, ExchangeBalance> entry : balanceMap.entrySet()) {
			BaseExchange ex = entry.getKey();
			BigDecimal futuresBalance = entry.getValue().futuresFreeUsdt();
			BigDecimal spotBalance = entry.getValue().spotFreeUsdt();
			if (futuresBalance.compareTo(required) >= 0 && spotBalance.compareTo(required) >= 0) continue;

			BigDecimal toTransfer = required.subtract(futuresBalance.min(spotBalance));
			InternalAccount from = futuresBalance.compareTo(required) < 0 ? InternalAccount.SPOT : InternalAccount.FUTURES;
			InternalAccount to = from == InternalAccount.SPOT ? InternalAccount.FUTURES : InternalAccount.SPOT;

			futures.add(ex.privateHttpClient().internalTransfer(new InternalTransfer(from, to, toTransfer))
							.exceptionally(t -> {
								log.error("Failed to transfer {} to futures on {}: {}", toTransfer, ex.name(), t.getMessage());
								throw new RuntimeException(t);
							}));
		}

		internalTransfersFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
						.thenRun(() -> log.info("Internal transfers completed"))
						.exceptionally((t) -> {
							this.shutdown();
							throw new RuntimeException(t);
						});
	}

	@Override
	protected void processTick(@NonNull CoinVector<CoinOpportunity> bestOpportunities) {
		bestOpportunities
						.filter(CoinOpportunity::goodEnough)
						.sortDesc(Comparator.comparing(CoinOpportunity::expectedGain))
						.forEach(this::attemptEnter);
	}

	private void attemptEnter(Map.Entry<String, CoinOpportunity> opEntry) {
		String coin = opEntry.getKey();
		CoinOpportunity op = opEntry.getValue();

		if (!internalTransfersFuture.isDone()) return;
		if (enteredCoins.containsKey(coin)) throw new RuntimeException("Coin " + coin + " is already entered.");
		if (usedExchanges.contains(op.exchanges().longEx())) return;
		if (usedExchanges.contains(op.exchanges().shortEx())) return;

		usedExchanges.add(op.exchanges().longEx());
		usedExchanges.add(op.exchanges().shortEx());

		try {
			enteredCoins.put(
							coin, new InTradeCoinLogic(
											coin,
											op,
											config.legUsdtAmount(),
											monitor,
											inTradeStrategyFactory.create(op),
											executionFactory.create(coin, op, config),
											schedulerBuilder
							)
			);
		} catch (Exception e) {
			usedExchanges.remove(op.exchanges().longEx());
			usedExchanges.remove(op.exchanges().shortEx());
			log.error("Failed to enter coin {}: {}", coin, e.getMessage());
		}
	}

	private void processExits() {
		for (Map.Entry<String, InTradeCoinLogic> entry : enteredCoins.entrySet()) {
			String coin = entry.getKey();
			InTradeCoinLogic logic = entry.getValue();

			CompletableFuture<Void> exitFuture = logic.exitTradeIfShould();
			if (exitFuture == null) continue;

			enteredCoins.remove(coin, logic);

			CompletableFuture<Void> handledExitFuture = exitFuture.handleAsync((ignored, throwable) -> {
				if (throwable != null) log.error("Failed to exit coin {}", coin, throwable);
				else {
					usedExchanges.remove(logic.opportunity().exchanges().longEx());
					usedExchanges.remove(logic.opportunity().exchanges().shortEx());
				}
				return null;
			});
			exitFutures.add(handledExitFuture);
			handledExitFuture.whenComplete((ignored, throwable) -> exitFutures.remove(handledExitFuture));
		}
	}

	@Override
	public void shutdown() {
		super.shutdown();
		this.exitScheduler.cancelNow();
		this.exitFutures.parallelStream().forEach(CompletableFuture::join);
	}
}
