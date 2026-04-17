package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;

public class TestCoinExecution extends CoinExecution {
	public TestCoinExecution(
					@NonNull String coin,
					ExchangePair exchanges,
					TradeParams tradeParams,
					Leverages leverages,
					TradeDirections tradeDirections
	) {
		super(coin, exchanges, tradeParams, leverages, tradeDirections);
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
