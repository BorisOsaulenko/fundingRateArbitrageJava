package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.logic.TradeLogger;
import com.boris.fundingarbitrage.model.assetops.*;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public class ClassicSingleCoinExecution extends SingleCoinExecution {
	private static final MarginMode FUTURES_MARGIN_MODE = MarginMode.CROSS;

	private final BigDecimal baseAssetQty;
	private final int contractQty;
	private final int leverage;

	public ClassicSingleCoinExecution(
					@NonNull String coin,
					@NonNull BaseExchange exchange,
					@NonNull BigDecimal baseAssetQty,
					int contractQty,
					int leverage,
					TradeLogger tradeLogger
	) {
		super(
						coin,
						tradeLogger,
						new TradeDirections(TradeMarket.SPOT, TradeMarket.FUTURES),
						exchange
		);
		this.baseAssetQty = baseAssetQty;
		this.contractQty = contractQty;
		this.leverage = leverage;
	}

	@Override
	protected CompletableFuture<TradeIds> enterInternal() {
		SpotOrder spotOrder = new SpotOrder(OrderSide.LONG, TradeSide.OPEN, baseAssetQty);
		FuturesOrder futuresOrder = new FuturesOrder(
						OrderSide.SHORT,
						TradeSide.OPEN,
						baseAssetQty,
						contractQty,
						leverage,
						FUTURES_MARGIN_MODE
		);

		CompletableFuture<String> spotFuture = exchange.privateHttpClient.placeSpotOrder(coin, spotOrder)
						.whenComplete((orderId, t) -> logCompletion("spot open order", orderId, t));
		CompletableFuture<Void> marginModeFuture = exchange.privateHttpClient.setMarginMode(coin, FUTURES_MARGIN_MODE)
						.whenComplete((_, t) -> logCompletion("futures margin mode update", FUTURES_MARGIN_MODE.name(), t));
		CompletableFuture<Void> leverageFuture = exchange.privateHttpClient.changeLeverage(coin, leverage)
						.whenComplete((_, t) -> logCompletion("futures leverage update", String.valueOf(leverage), t));
		CompletableFuture<String> futuresFuture = CompletableFuture.allOf(marginModeFuture, leverageFuture)
						.thenCompose(_ -> exchange.privateHttpClient.placeFuturesOrder(coin, futuresOrder))
						.whenComplete((orderId, t) -> logCompletion("futures open order", orderId, t));

		return CompletableFuture.allOf(spotFuture, marginModeFuture, leverageFuture, futuresFuture)
						.thenApply(_ -> new TradeIds(spotFuture.join(), futuresFuture.join()));
	}

	@Override
	protected CompletableFuture<TradeIds> exitInternal() {
		SpotOrder spotOrder = new SpotOrder(OrderSide.LONG, TradeSide.CLOSE, baseAssetQty);
		FuturesOrder futuresOrder = new FuturesOrder(
						OrderSide.SHORT,
						TradeSide.CLOSE,
						baseAssetQty,
						contractQty,
						leverage,
						FUTURES_MARGIN_MODE
		);

		CompletableFuture<String> spotFuture = exchange.privateHttpClient.placeSpotOrder(coin, spotOrder)
						.whenComplete((orderId, t) -> logCompletion("spot close order", orderId, t));
		CompletableFuture<String> futuresFuture = exchange.privateHttpClient.placeFuturesOrder(coin, futuresOrder)
						.whenComplete((orderId, t) -> logCompletion("futures close order", orderId, t));

		return CompletableFuture.allOf(spotFuture, futuresFuture)
						.thenApply(_ -> new TradeIds(spotFuture.join(), futuresFuture.join()));
	}

	private void logCompletion(String action, String successValue, Throwable t) {
		if (t == null) {
			tradeLogger.log("Successful %s: %s", action, successValue);
			return;
		}
		tradeLogger.error("Failed %s: %s", action, t.getMessage());
	}
}
