package com.boris.fundingarbitrage.logic.implementations;

import com.boris.fundingarbitrage.coinfilter.CoinAvailabilityRecord;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.ArbitrageLogic;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.logic.TradeSession;
import com.boris.fundingarbitrage.logic.balancespolicy.IBalancesPolicy;
import com.boris.fundingarbitrage.logic.factory.TradeSessionFactory;
import com.boris.fundingarbitrage.logic.opportunityanalyzer.IOpportunityAnalyzer;
import com.boris.fundingarbitrage.model.assetops.InternalAccount;
import com.boris.fundingarbitrage.model.assetops.InternalTransfer;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;
import com.boris.fundingarbitrage.monitor.ICoinMonitor;
import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableSchedulerBuilder;
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
	private final TradeSessionFactory tradeSessionFactory;
	private final Set<BaseExchange> usedExchanges = new CopyOnWriteArraySet<>();
	private final Map<String, TradeSession> enteredCoins = new ConcurrentHashMap<>();
	private final IModifiableScheduler exitScheduler;
	private final Set<CompletableFuture<Void>> exitFutures = new CopyOnWriteArraySet<>();
	private CompletableFuture<Void> internalTransfersFuture;

	public RebalancingArbitrageLogic(
					ICoinMonitor monitor,
					IOpportunityAnalyzer opportunityAnalyzer,
					PreTradeStrategy preTradeStrategy,
					CoinAvailabilityRecord coinAvailability,
					ArbitrageBotConfig arbConfig,
					IBalancesPolicy balancesPolicy,
					TradeSessionFactory tradeSessionFactory,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(
						monitor,
						opportunityAnalyzer,
						preTradeStrategy,
						coinAvailability,
						arbConfig,
						balancesPolicy,
						schedulerBuilder
		);
		this.tradeSessionFactory = tradeSessionFactory;
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

		TradeSession session = tradeSessionFactory.create(coin, op);
		enteredCoins.put(coin, session);
		session.enter(_ -> {
			enteredCoins.remove(coin, session);
			usedExchanges.remove(op.exchanges().longEx());
			usedExchanges.remove(op.exchanges().shortEx());
		});
	}

	private void processExits() {
		for (Map.Entry<String, TradeSession> entry : enteredCoins.entrySet()) {
			String coin = entry.getKey();
			TradeSession logic = entry.getValue();

			CompletableFuture<Void> exitFuture = logic.exitTradeIfShould(() -> {
				usedExchanges.remove(logic.getOp().exchanges().longEx());
				usedExchanges.remove(logic.getOp().exchanges().shortEx());
			});
			if (exitFuture == null) continue;

			enteredCoins.remove(coin, logic);

			exitFutures.add(exitFuture);
			exitFuture.whenComplete((ignored, throwable) -> exitFutures.remove(exitFuture));
		}
	}

	@Override
	public void shutdown() {
		super.shutdown();
		this.exitScheduler.cancelNow();
		this.exitFutures.parallelStream().forEach(CompletableFuture::join);
	}
}
