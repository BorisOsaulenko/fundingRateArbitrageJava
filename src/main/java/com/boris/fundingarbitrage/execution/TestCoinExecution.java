package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import lombok.NonNull;

import java.util.concurrent.CompletableFuture;

public class TestCoinExecution extends CoinExecution {
	public TestCoinExecution(
					@NonNull String coin,
					@NonNull CoinOpportunity op,
					@NonNull ArbitrageBotConfig config
	) {
		super(coin, op, config);
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
