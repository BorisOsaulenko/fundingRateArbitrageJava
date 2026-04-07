package com.boris.fundingarbitrage.tradelogger;

import com.boris.fundingarbitrage.execution.TradeIds;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;

import java.util.concurrent.CompletableFuture;

public abstract non-sealed class CrossTradeLogger extends TradeLogger {
	public CrossTradeLogger(String coin) {
		super(coin);
	}

	public abstract void setup(ExchangeConstantData longCd, ExchangeConstantData shortCd);

	public abstract void logEnter(ExchangeSnapshot longSn, ExchangeSnapshot shortSn);

	public abstract void logFunding(ExchangeSnapshot longSn, ExchangeSnapshot shortSn);

	public abstract void logExit(ExchangeSnapshot longSn, ExchangeSnapshot shortSn);

	public abstract CompletableFuture<Void> finish(TradeIds enterIds, TradeIds exitIds);
}
