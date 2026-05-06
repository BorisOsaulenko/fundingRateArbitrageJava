package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import lombok.Getter;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public abstract class CoinExecution {
	protected final String coin;
	protected final TradeParams tradeParams;
	protected final Leverages leverages;
	protected final CoinOpportunity op;
	protected final ArbitrageBotConfig config;
	protected CompletableFuture<Void> enterFuture = null;
	protected CompletableFuture<Void> exitFuture = null;
	@Getter protected TradeIds enterIds;
	@Getter protected TradeIds exitIds;
	private boolean failed = false;

	protected CoinExecution(
					@NonNull String coin,
					@NonNull CoinOpportunity op,
					@NonNull ArbitrageBotConfig config
	) {
		this.coin = coin;
		this.op = op;
		this.config = config;
		this.tradeParams = getEnterParams(op.longData().snapshot(), op.shortData().snapshot());

		int longLeverage = op.longData().market() == TradeMarket.FUTURES ? config.leverage() : 1;
		int shortLeverage = op.shortData().market() == TradeMarket.FUTURES ? config.leverage() : 1;
		this.leverages = new Leverages(longLeverage, shortLeverage);
	}

	private static BigDecimal lcm(BigDecimal a, BigDecimal b) {
		int scale = Math.max(a.scale(), b.scale());

		BigInteger aInt = a.movePointRight(scale).toBigIntegerExact();
		BigInteger bInt = b.movePointRight(scale).toBigIntegerExact();

		BigInteger gcd = aInt.gcd(bInt);
		BigInteger lcm = aInt.divide(gcd).multiply(bInt);

		return new BigDecimal(lcm, scale);
	}

	protected abstract CompletableFuture<TradeIds> enterInternal();

	public CompletableFuture<Void> enterTrade() {
		if (failed) return CompletableFuture.completedFuture(null);
		if (enterFuture != null) return enterFuture;

		return this.enterFuture = enterInternal().thenAccept(ids -> {
			this.enterIds = ids;
		}).exceptionally(t -> {
			failed = true;
			return null;
		});
	}

	protected abstract CompletableFuture<TradeIds> exitInternal();

	public CompletableFuture<Void> exitTrade() {
		if (!shouldAttemptExit()) return CompletableFuture.completedFuture(null);
		if (exitFuture != null) return exitFuture;

		return this.exitFuture = exitInternal().thenAccept(ids -> {
			this.exitIds = ids;
		}).exceptionally(t -> {
			failed = true;
			return null;
		});
	}

	private boolean shouldAttemptExit() {
		if (failed) return false;
		if (enterFuture == null) return false;
		return enterFuture.state().equals(Future.State.SUCCESS);
	}

	private TradeParams getEnterParams(Snapshot longEnter, Snapshot shortEnter) {
		BigDecimal baseAssetQty = getBaseAssetQty(longEnter, shortEnter);

		if (baseAssetQty.equals(BigDecimal.ZERO))
			throw new RuntimeException("Not enough margin deposited for coin: " + coin + ". Did not enter trades");

		BigDecimal longLotSize = op.longData().constantData().lotSize();
		BigDecimal shortLotSize = op.shortData().constantData().lotSize();

		int longContractQty = baseAssetQty.divide(longLotSize, RoundingMode.FLOOR).intValueExact();
		int shortContractQty = baseAssetQty.divide(shortLotSize, RoundingMode.FLOOR).intValueExact();

		return new TradeParams(baseAssetQty, longContractQty, shortContractQty);
	}

	private BigDecimal getBaseAssetQty(Snapshot longEnter, Snapshot shortEnter) {
		BigDecimal longLotSize = op.longData().constantData().lotSize(); // 2 COIN
		BigDecimal shortLotSize = op.shortData().constantData().lotSize(); // 3 COIN
		BigDecimal effectiveLotSize = lcm(longLotSize, shortLotSize); // 6 COIN

		BigDecimal longAsk = longEnter.bookTicker().askPrice(); // 10 usdt/COIN
		BigDecimal shortBid = shortEnter.bookTicker().bidPrice(); // 15 usdt/COIN

		BigDecimal longELSMultiplier = config.legUsdtAmount() // 300 usdt
						.divide(longAsk, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 5 -

		BigDecimal shortELSMultiplier = config.legUsdtAmount()
						.divide(shortBid, RoundingMode.FLOOR)
						.divide(effectiveLotSize, RoundingMode.FLOOR); // 3 -

		BigDecimal effectiveELSMultiplier = longELSMultiplier.min(shortELSMultiplier); // 3 -
		return effectiveELSMultiplier.multiply(effectiveLotSize); // 18 COIN
	}
}
