package com.boris.fundingarbitrage.intradelogic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.execution.factory.CoinExecutionFactory;
import com.boris.fundingarbitrage.execution.factory.TestCoinExecutionFactory;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.tradelogger.TradeLogger;
import lombok.Getter;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class InTradeCoinLogic {
	@Getter protected final ExchangePair exchanges;
	protected final Snapshot longEnterSn;
	protected final Snapshot shortEnterSn;
	protected final TradeMarket longMarket;
	protected final TradeMarket shortMarket;
	protected final InTradeStrategy strategy;
	protected final ConstantData longConstantData;
	protected final ConstantData shortConstantData;
	@Getter protected final String coin;
	protected final CoinExecution execution;
	private final ScheduledExecutorService fundingRegisterExecutor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean shouldRegisterShortFunding = new AtomicBoolean();
	private final AtomicBoolean shouldRegisterLongFunding = new AtomicBoolean();
	private final AtomicLong longSettlementUtc = new AtomicLong(0);
	private final AtomicLong shortSettlementUtc = new AtomicLong(0);
	private final CoinMonitor monitor;
	private final BigDecimal legUsdtAmount;
	private final TradeLogger tradeLogger;
	protected CoinExecutionFactory executionFactory = new TestCoinExecutionFactory();
	protected CompletableFuture<Void> enterFuture;

	public InTradeCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull InTradeStrategy strategy,
					@NonNull BigDecimal legUsdtAmount,
					@NonNull ExchangePair exchanges,
					@NonNull Leverages leverages,
					@NonNull TradeDirections tradeDirections,
					@NonNull ConstantData longCD,
					@NonNull ConstantData shortCD
	) {
		this.coin = coin;
		this.monitor = monitor;
		this.legUsdtAmount = legUsdtAmount;
		this.strategy = strategy;
		this.longEnterSn = monitor.getFuturesSnapshot(exchanges.longEx(), coin);
		this.shortEnterSn = monitor.getFuturesSnapshot(exchanges.shortEx(), coin);

		this.exchanges = exchanges;
		this.longMarket = tradeDirections.longMarket();
		this.shortMarket = tradeDirections.shortMarket();
		this.longConstantData = longCD;
		this.shortConstantData = shortCD;

		BigDecimal baseAssetQty = getBaseAssetQty(longEnterSn, shortEnterSn);
		this.tradeLogger = new TradeLogger(coin, exchanges, tradeDirections, baseAssetQty, longCD, shortCD);

		this.execution = executionFactory.create(
						coin,
						exchanges,
						getEnterParams(longEnterSn, shortEnterSn),
						leverages,
						tradeDirections
		);
		this.enterFuture = execution.enterTrade().exceptionally(tradeLogger::logEnterFailure);

		this.shouldRegisterLongFunding.set(longMarket == TradeMarket.FUTURES);
		this.shouldRegisterShortFunding.set(shortMarket == TradeMarket.FUTURES);
		this.fundingRegisterExecutor.scheduleAtFixedRate(this::registerFunding, 0, 30, TimeUnit.MINUTES);

		tradeLogger.logEnterSuccess(longEnterSn, shortEnterSn);
	}

	private static BigDecimal lcm(BigDecimal a, BigDecimal b) {
		int scale = Math.max(a.scale(), b.scale());

		BigInteger aInt = a.movePointRight(scale).toBigIntegerExact();
		BigInteger bInt = b.movePointRight(scale).toBigIntegerExact();

		BigInteger gcd = aInt.gcd(bInt);
		BigInteger lcm = aInt.divide(gcd).multiply(bInt);

		return new BigDecimal(lcm, scale);
	}

	private TradeParams getEnterParams(Snapshot longEnter, Snapshot shortEnter) {
		BigDecimal baseAssetQty = getBaseAssetQty(longEnter, shortEnter);

		if (baseAssetQty.equals(BigDecimal.ZERO))
			throw new RuntimeException("Not enough margin deposited for coin: " + coin + ". Did not enter trades");

		BigDecimal longLotSize = longConstantData.lotSize();
		BigDecimal shortLotSize = shortConstantData.lotSize();

		int longContractQty = baseAssetQty.divide(longLotSize, RoundingMode.FLOOR).intValueExact();
		int shortContractQty = baseAssetQty.divide(shortLotSize, RoundingMode.FLOOR).intValueExact();

		return new TradeParams(baseAssetQty, longContractQty, shortContractQty);
	}

	protected void registerFunding() {
		if (shouldRegisterLongFunding.get()) registerFunding(exchanges.longEx(), true);
		if (shouldRegisterShortFunding.get()) registerFunding(exchanges.shortEx(), false);
	}

	private void registerFunding(BaseExchange ex, boolean isLong) {
		AtomicBoolean shouldRegister = isLong ? shouldRegisterLongFunding : shouldRegisterShortFunding;
		AtomicLong settlementUtc = isLong ? longSettlementUtc : shortSettlementUtc;
		if (shouldRegister.get()) {
			shouldRegister.set(false);
			FuturesSnapshot snapshot = monitor.getFuturesSnapshot(ex, coin);
			settlementUtc.set(snapshot.funding().settlement().toEpochMilli());
			monitor.completionAgent.performOnTimestamp(
							settlementUtc.get(), ex, coin, (sn, _) -> {
								tradeLogger.logFunding(snapshot, isLong);
								strategy.registerFunding(sn, isLong);
								shouldRegister.set(true);
							}
			);
		}
	}

	protected BigDecimal getBaseAssetQty(Snapshot longEnter, Snapshot shortEnter) {
		BigDecimal longLotSize = longConstantData.lotSize(); // 2 COIN
		BigDecimal shortLotSize = shortConstantData.lotSize(); // 3 COIN
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize); // 6 COIN

		BigDecimal longAsk = longEnter.bookTicker().askPrice(); // 10 usdt/COIN
		BigDecimal shortBid = shortEnter.bookTicker().bidPrice(); // 15 usdt/COIN

		BigDecimal longELSMultiplier = this.legUsdtAmount // 300 usdt
						.divide(longAsk, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 5 -

		BigDecimal shortELSMultiplier = this.legUsdtAmount
						.divide(shortBid, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 3 -

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier); // 3 -
		return effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN
	}

	private CompletableFuture<Void> shutdown(Snapshot currLong, Snapshot currShort) {
		return new CompletableFuture<>().completeOnTimeout(
						null,
						5,
						TimeUnit.SECONDS
		).thenCompose(v -> {
			tradeLogger.logExit(currLong, currShort);
			return tradeLogger.finish(execution.getEnterIds(), execution.getExitIds());
		});
	}

	public CompletableFuture<Void> exitTradeIfShould() {
		if (!enterFuture.isDone()) return null;

		Snapshot currLong = monitor.getSnapshot(exchanges.longEx(), coin, longMarket);
		Snapshot currShort = monitor.getSnapshot(exchanges.shortEx(), coin, shortMarket);
		if (!strategy.shouldExitTrade(currLong, currShort)) return null;

		return execution.exitTrade().thenCompose(v -> shutdown(currLong, currShort));
	}

	protected void finish() {
		fundingRegisterExecutor.shutdownNow();
		monitor.completionAgent.cancelTimestampExecution(exchanges.longEx(), coin, longSettlementUtc.get());
		monitor.completionAgent.cancelTimestampExecution(exchanges.shortEx(), coin, shortSettlementUtc.get());
	}
}
