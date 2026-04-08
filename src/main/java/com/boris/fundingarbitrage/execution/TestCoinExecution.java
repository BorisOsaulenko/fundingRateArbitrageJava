package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;

public class TestCoinExecution extends CoinExecution {
	public TestCoinExecution(
					@NonNull String coin,
					ExchangePair exchanges,
					TradeDirections tradeDirections
	) {
		super(coin, exchanges, tradeDirections);
	}

	@Override
	protected CompletableFuture<TradeIds> enterInternal() {
		return CompletableFuture.completedFuture(new TradeIds("longEnterId", "shortEnterId"));
	}

	@Override
	protected CompletableFuture<TradeIds> exitInternal() {
		return CompletableFuture.completedFuture(new TradeIds("longExitId", "shortExitId"));
	}
}
