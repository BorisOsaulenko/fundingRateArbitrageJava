package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.tradelogger.TradeLogger;
import lombok.NonNull;

public abstract non-sealed class SingleCoinExecution extends CoinExecution {
	protected final BaseExchange exchange;

	public SingleCoinExecution(
					@NonNull String coin,
					TradeLogger tradeLogger,
					TradeDirections tradeDirections,
					BaseExchange exchange
	) {
		super(coin, tradeLogger, tradeDirections);
		this.exchange = exchange;
		tradeLogger.log("Single Trade. Coin: %s. Exchange: %s", coin, exchange.name);
	}
}
