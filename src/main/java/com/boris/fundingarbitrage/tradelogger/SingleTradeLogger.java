package com.boris.fundingarbitrage.tradelogger;

import com.boris.fundingarbitrage.execution.TradeIds;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.util.concurrent.CompletableFuture;

public abstract non-sealed class SingleTradeLogger extends TradeLogger {
	public SingleTradeLogger(String coin) {
		super(coin);
	}

	public abstract void setup(ExchangeConstantData cd);

	public abstract void logEnter(ExchangeSnapshot sn);

	public abstract void logFunding(ExchangeSnapshot sn);

	public abstract void logExit(ExchangeSnapshot sn);

	public abstract CompletableFuture<Void> finish(TradeIds enterIds, TradeIds exitIds);
}
