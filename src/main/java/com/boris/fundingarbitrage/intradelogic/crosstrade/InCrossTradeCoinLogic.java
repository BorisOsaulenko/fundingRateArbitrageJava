package com.boris.fundingarbitrage.intradelogic.crosstrade;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.CrossCoinExecution;
import com.boris.fundingarbitrage.intradelogic.InTradeCoinLogic;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.intradestrategy.InCrossTradeStrategy;
import com.boris.fundingarbitrage.strategy.intradestrategy.cross.ClassicInCrossTradeStrategy;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
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

public abstract class InCrossTradeCoinLogic extends InTradeCoinLogic {
	@Getter protected final ExchangePair exchanges;
	protected final Snapshot longEnterSn;
	protected final Snapshot shortEnterSn;
	protected final TradeMarket longMarket;
	protected final TradeMarket shortMarket;
	protected final ConstantData longConstantData;
	protected final ConstantData shortConstantData;
	protected final InCrossTradeStrategy strategy;
	protected final AtomicBoolean fillsFetchSuccess = new AtomicBoolean(true);
	private final ScheduledExecutorService fundingRegisterExecutor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicBoolean shouldRegisterShortFunding = new AtomicBoolean();
	private final AtomicBoolean shouldRegisterLongFunding = new AtomicBoolean();
	private final AtomicLong longSettlementUtc = new AtomicLong(0);
	private final AtomicLong shortSettlementUtc = new AtomicLong(0);

	protected CrossCoinExecution execution;
	protected CompletableFuture<Void> enterFuture;

	public InCrossTradeCoinLogic(
					@NonNull String coin,
					@NonNull CoinMonitor monitor,
					@NonNull BigDecimal legUsdtAmount,
					@NonNull ExchangePair exchanges,
					@NonNull TradeDirections tradeDirections,
					@NonNull ConstantData longConstantData,
					@NonNull ConstantData shortConstantData
	) {
		super(coin, monitor, legUsdtAmount);

		this.longEnterSn = monitor.getFuturesSnapshot(exchanges.longEx(), coin);
		this.shortEnterSn = monitor.getFuturesSnapshot(exchanges.shortEx(), coin);

		this.strategy = new ClassicInCrossTradeStrategy(
						new ExchangeData(longEnterSn, longConstantData),
						new ExchangeData(shortEnterSn, shortConstantData),
						tradeDirections
		);

		this.exchanges = exchanges;
		this.longMarket = tradeDirections.longMarket();
		this.shortMarket = tradeDirections.shortMarket();
		this.longConstantData = longConstantData;
		this.shortConstantData = shortConstantData;

		this.shouldRegisterLongFunding.set(longMarket == TradeMarket.FUTURES);
		this.shouldRegisterShortFunding.set(shortMarket == TradeMarket.FUTURES);

		this.fundingRegisterExecutor.scheduleAtFixedRate(this::registerFunding, 0, 30, TimeUnit.MINUTES);

		tradeLogger.log(coin + ". Long: " + exchanges.longEx().name + ", Short: " + exchanges.shortEx().name);
	}

	private static BigDecimal lcm(BigDecimal a, BigDecimal b) {
		int scale = Math.max(a.scale(), b.scale());

		BigInteger aInt = a.movePointRight(scale).toBigIntegerExact();
		BigInteger bInt = b.movePointRight(scale).toBigIntegerExact();

		BigInteger gcd = aInt.gcd(bInt);
		BigInteger lcm = aInt.divide(gcd).multiply(bInt);

		return new BigDecimal(lcm, scale);
	}

	@Override
	protected void registerFunding() {
		if (shouldRegisterLongFunding.get()) registerFunding(exchanges.longEx(), true);
		if (shouldRegisterShortFunding.get()) registerFunding(exchanges.shortEx(), false);
	}

	private void registerFunding(BaseExchange ex, boolean isLong) {
		AtomicBoolean shouldRegister = isLong ? shouldRegisterLongFunding : shouldRegisterShortFunding;
		AtomicLong settlementUtc = isLong ? longSettlementUtc : shortSettlementUtc;
		if (shouldRegister.get()) {
			shouldRegister.set(false);
			ExchangeSnapshot snapshot = monitor.getFuturesSnapshot(ex, coin);
			settlementUtc.set(snapshot.fundingSettlement().toEpochMilli());
			monitor.performOnTimestamp(
							settlementUtc.get(), ex, coin, (sn) -> {
								tradeLogger.log("Funding on " + ex.name + ": [Rate: " + sn.fundingRate() + "]");
								strategy.registerFunding(sn, isLong);
								shouldRegister.set(true);
							}
			);
		}
	}

	protected BigDecimal getBaseAssetQty(ExchangeSnapshot longEnter, ExchangeSnapshot shortEnter) {
		BigDecimal longLotSize = longConstantData.lotSize(longMarket); // 2 COIN
		BigDecimal shortLotSize = shortConstantData.lotSize(shortMarket); // 3 COIN
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize); // 6 COIN

		BigDecimal longAsk = longEnter.bookTicker(longMarket).askPrice(); // 10 usdt/COIN
		BigDecimal shortBid = shortEnter.bookTicker(shortMarket).bidPrice(); // 15 usdt/COIN

		BigDecimal longELSMultiplier = this.legUsdtAmount // 300 usdt
						.divide(longAsk, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 5 -

		BigDecimal shortELSMultiplier = this.legUsdtAmount
						.divide(shortBid, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 3 -

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier); // 3 -
		return effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN
	}

	private CompletableFuture<Void> shutdown(ExchangeSnapshot currLong, ExchangeSnapshot currShort) {
		return new CompletableFuture<>().completeOnTimeout(
						null,
						5,
						TimeUnit.SECONDS
		).thenCompose(v -> logTradeInfo(currLong, currShort));
	}

	@Override
	public CompletableFuture<Void> exitTradeIfShould() {
		if (!enterFuture.isDone()) return null;

		ExchangeSnapshot currLong = monitor.getFuturesSnapshot(exchanges.longEx(), coin);
		ExchangeSnapshot currShort = monitor.getFuturesSnapshot(exchanges.shortEx(), coin);
		if (!strategy.shouldExitTrade(currLong, currShort)) return null;

		return execution.exitTrade().thenCompose(v -> shutdown(currLong, currShort));
	}

	protected Void failEnter(Throwable t) {
		tradeLogger.error("Failed to enter trade for " + coin + ". Long: "
											+ exchanges.longEx().name + " and short: " + exchanges.shortEx().name);
		tradeLogger.error("Message: " + t.getMessage());
		throw new RuntimeException(t);
	}

	@Override
	protected void finish() {
		fundingRegisterExecutor.shutdownNow();
		monitor.cancelTimestampExecution(longSettlementUtc.get(), exchanges.longEx(), coin);
		monitor.cancelTimestampExecution(shortSettlementUtc.get(), exchanges.shortEx(), coin);
	}
}
