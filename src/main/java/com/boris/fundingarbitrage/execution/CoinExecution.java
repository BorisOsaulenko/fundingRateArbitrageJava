package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.assetops.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CoinExecution {
	private static boolean instanceCreated = false;
	private final Map<Integer, TradeData> trades = new ConcurrentHashMap<>();
	private final int leverage;
	private int tradeId = 0;

	public CoinExecution(int leverage) {
		if (instanceCreated) throw new IllegalStateException("CoinExecution instance already created");
		instanceCreated = true;
		this.leverage = leverage;
	}

	private FuturesOrder getLongOrder(BigDecimal baseAssetQty, int contractQty, boolean opening) {
		TradeSide tradeSide = opening ? TradeSide.OPEN : TradeSide.CLOSE;
		return new FuturesOrder(OrderSide.LONG, tradeSide, baseAssetQty, contractQty, leverage, MarginMode.CROSS);
	}

	private FuturesOrder getShortOrder(BigDecimal baseAssetQty, int contractQty, boolean opening) {
		TradeSide tradeSide = opening ? TradeSide.OPEN : TradeSide.CLOSE;
		return new FuturesOrder(OrderSide.SHORT, tradeSide, baseAssetQty, contractQty, leverage, MarginMode.CROSS);
	}

	public CompletableFuture<TradeIds> enterTrade(EnterParams params) {
		FuturesOrder longOrder = getLongOrder(params.baseAssetQty(), params.longContractQty(), true);
		FuturesOrder shortOrder = getShortOrder(params.baseAssetQty(), params.shortContractQty(), true);

		CompletableFuture<String> LEnter = params.longEx().privateHttpClient.placeFuturesOrder(params.coin(), longOrder);
		CompletableFuture<String> SEnter = params.shortEx().privateHttpClient.placeFuturesOrder(params.coin(), shortOrder);

		return CompletableFuture.allOf(LEnter, SEnter).thenApply(_ -> {
			String longId = LEnter.join();
			String shortId = SEnter.join();

			int internalTradeId = tradeId++;
			TradeData tradeData = new TradeData(
							params.coin(),
							params.longEx(),
							params.shortEx(),
							longId,
							shortId,
							params.baseAssetQty(),
							params.longContractQty(),
							params.shortContractQty()
			);

			trades.put(internalTradeId, tradeData);
			return new TradeIds(internalTradeId, longId, shortId);
		});
	}

	public CompletableFuture<TradeIds> exitTrade(int tradeId) {
		TradeData tradeData = trades.get(tradeId);
		if (tradeData == null) throw new IllegalArgumentException("Trade with id not found");

		FuturesOrder longOrder = getLongOrder(tradeData.baseAssetQty(), tradeData.longContractQty(), false);
		FuturesOrder shortOrder = getShortOrder(tradeData.baseAssetQty(), tradeData.shortContractQty(), false);

		String coin = tradeData.coin();
		CompletableFuture<String> LExit = tradeData.longEx().privateHttpClient.placeFuturesOrder(coin, longOrder);
		CompletableFuture<String> SExit = tradeData.shortEx().privateHttpClient.placeFuturesOrder(coin, shortOrder);

		return CompletableFuture.allOf(LExit, SExit).thenApply(_ -> {
			String longId = LExit.join();
			String shortId = SExit.join();
			trades.remove(tradeId);
			return new TradeIds(tradeId, longId, shortId);
		});
	}

	private record TradeData(
					String coin,
					BaseExchange longEx,
					BaseExchange shortEx,
					String longId,
					String shortId,
					BigDecimal baseAssetQty,
					int longContractQty,
					int shortContractQty
	) {
	}

	public record TradeIds(int internalTradeId, String longId, String shortId) {
	}
}
