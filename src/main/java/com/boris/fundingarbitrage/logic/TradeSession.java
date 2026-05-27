package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.ITradeExecution;
import com.boris.fundingarbitrage.execution.factory.TradeExecutionFactory;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;
import com.boris.fundingarbitrage.tradelogger.TradeSessionLogger;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class TradeSession {
	private final CoinOpportunity op;
	@Getter private final String coin;
	private final CoinMonitor monitor;
	private final InTradeStrategy strategy;
	private final ITradeExecution execution;
	private final TradeSessionLogger tradeLogger;
	private final IModifiableScheduler fundingRegisterScheduler;
	private final AtomicBoolean shouldRegisterShortFunding;
	private final AtomicBoolean shouldRegisterLongFunding;
	private CompletableFuture<Void> enterFuture;

	public TradeSession(
					String coin,
					CoinOpportunity op,
					ArbitrageBotConfig config,
					CoinMonitor monitor,
					InTradeStrategy strategy,
					TradeExecutionFactory executionFactory,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		this.coin = coin;
		this.op = op;
		this.monitor = monitor;
		this.strategy = strategy;
		this.tradeLogger = new TradeSessionLogger(coin, op, config.legUsdtAmount());
		this.execution = executionFactory.create(coin, op, config, tradeLogger);

		this.shouldRegisterLongFunding = new AtomicBoolean(op.longData().market() == TradeMarket.FUTURES);
		this.shouldRegisterShortFunding = new AtomicBoolean(op.shortData().market() == TradeMarket.FUTURES);
		this.fundingRegisterScheduler = schedulerBuilder.create(this::registerFunding, 30, TimeUnit.MINUTES);
	}

	public CompletableFuture<Void> enter() {
		return enter(null);
	}

	public CompletableFuture<Void> enter(Consumer<Throwable> onError) {
		if (enterFuture != null) throw new IllegalStateException("Trade session already entered.");

		enterFuture = execution.enterTrade()
						.thenRun(fundingRegisterScheduler::start)
						.whenComplete((ignored, throwable) -> {
							if (throwable == null) return;

							if (onError != null) onError.accept(throwable);
						});
		return enterFuture;
	}

	public CoinOpportunity opportunity() {
		return op;
	}

	protected void registerFunding() {
		if (shouldRegisterLongFunding.get()) registerFunding(op.exchanges().longEx(), true);
		if (shouldRegisterShortFunding.get()) registerFunding(op.exchanges().shortEx(), false);
	}

	private void registerFunding(BaseExchange ex, boolean isLong) {
		AtomicBoolean shouldRegister = isLong ? shouldRegisterLongFunding : shouldRegisterShortFunding;
		if (shouldRegister.get()) {
			shouldRegister.set(false);
			FuturesSnapshot snapshot = monitor.getFuturesSnapshot(ex, coin);
			long settlement = snapshot.funding().settlement().toEpochMilli();
			monitor.completionAgent.performOnTimestamp(
							settlement, ex, coin, (sn, _) -> {
								tradeLogger.logFunding(snapshot, isLong);
								strategy.registerFunding(sn, isLong);
								shouldRegister.set(true);
							}
			);
		}
	}

	private CompletableFuture<Void> shutdown() {
		return new CompletableFuture<>().completeOnTimeout(
						null,
						5,
						TimeUnit.SECONDS
		).thenCompose(_ -> {
			fundingRegisterScheduler.cancelNow();
			return tradeLogger.finish(execution.getEnterIds(), execution.getExitIds());
		});
	}

	public CompletableFuture<Void> exitTradeIfShould(Runnable onSuccess) {
		if (enterFuture == null) return null;
		if (!enterFuture.state().equals(Future.State.SUCCESS)) return null;

		Snapshot currLong = monitor.getSnapshot(op.exchanges().longEx(), coin, op.longData().market());
		Snapshot currShort = monitor.getSnapshot(op.exchanges().shortEx(), coin, op.shortData().market());
		if (!strategy.shouldExitTrade(currLong, currShort)) return null;

		return execution.exitTrade(currLong, currShort)
						.thenCompose(_ -> shutdown())
						.whenComplete((ignored, throwable) -> {
							if (throwable != null) return;
							if (onSuccess != null) onSuccess.run();
						});
	}
}
