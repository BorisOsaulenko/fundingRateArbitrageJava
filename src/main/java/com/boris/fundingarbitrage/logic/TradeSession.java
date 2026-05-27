package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;
import com.boris.fundingarbitrage.tradelogger.TradeLogger;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class TradeSession {
	private final CoinOpportunity op;
	@Getter private final String coin;
	private final CoinMonitor monitor;
	private final InTradeStrategy strategy;
	private final CoinExecution execution;
	private CompletableFuture<Void> enterFuture;
	private final TradeLogger tradeLogger;

	private final IModifiableScheduler fundingRegisterScheduler;
	private final AtomicBoolean shouldRegisterShortFunding;
	private final AtomicBoolean shouldRegisterLongFunding;

	public TradeSession(
					String coin,
					CoinOpportunity op,
					BigDecimal legUsdtAmount,
					CoinMonitor monitor,
					InTradeStrategy strategy,
					CoinExecution execution,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		this.coin = coin;
		this.op = op;
		this.monitor = monitor;
		this.strategy = strategy;
		this.tradeLogger = new TradeLogger(coin, op, legUsdtAmount);
		this.execution = execution;

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
						.thenRun(() -> {
							logEnterSuccess();
							fundingRegisterScheduler.start();
						})
						.whenComplete((ignored, throwable) -> {
							if (throwable == null) return;

							tradeLogger.logEnterFailure(throwable);
							if (onError != null) onError.accept(throwable);
						});
		return enterFuture;
	}

	public CoinOpportunity opportunity() {
		return op;
	}

	private void logEnterSuccess() {
		BaseExchange longEx = op.exchanges().longEx();
		BaseExchange shortEx = op.exchanges().shortEx();

		Snapshot longSnapshot = monitor.getSnapshot(longEx, coin, op.longData().market());
		Snapshot shortSnapshot = monitor.getSnapshot(shortEx, coin, op.shortData().market());

		tradeLogger.logEnterSuccess(longSnapshot, shortSnapshot);
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

	private CompletableFuture<Void> shutdown(Snapshot currLong, Snapshot currShort) {
		return new CompletableFuture<>().completeOnTimeout(
						null,
						5,
						TimeUnit.SECONDS
		).thenCompose(_ -> {
			tradeLogger.logExit(currLong, currShort);
			fundingRegisterScheduler.cancelNow();
			return tradeLogger.finish(execution.getEnterIds(), execution.getExitIds());
		});
	}

	public CompletableFuture<Void> exitTradeIfShould() {
		return exitTradeIfShould(null);
	}

	public CompletableFuture<Void> exitTradeIfShould(Runnable onSuccess) {
		if (enterFuture == null) return null;
		if (!enterFuture.isDone()) return null;

		Snapshot currLong = monitor.getSnapshot(op.exchanges().longEx(), coin, op.longData().market());
		Snapshot currShort = monitor.getSnapshot(op.exchanges().shortEx(), coin, op.shortData().market());
		if (!strategy.shouldExitTrade(currLong, currShort)) return null;

		return execution.exitTrade()
						.thenCompose(_ -> shutdown(currLong, currShort))
						.whenComplete((ignored, throwable) -> {
							if (throwable != null) {
								tradeLogger.logExitFailure(throwable, op.exchanges().longEx());
								tradeLogger.logExitFailure(throwable, op.exchanges().shortEx());
								return;
							}
							if (onSuccess != null) onSuccess.run();
						});
	}
}
