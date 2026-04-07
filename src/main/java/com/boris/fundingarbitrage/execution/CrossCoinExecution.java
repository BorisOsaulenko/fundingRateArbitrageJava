package com.boris.fundingarbitrage.execution;

import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;
import com.boris.fundingarbitrage.tradelogger.TradeLogger;
import lombok.NonNull;

public abstract non-sealed class CrossCoinExecution extends CoinExecution {
	protected ExchangePair exchanges;

	public CrossCoinExecution(
					@NonNull String coin,
					TradeLogger tradeLogger,
					TradeDirections tradeDirections,
					ExchangePair exchanges
	) {
		super(coin, tradeLogger, tradeDirections);
		this.exchanges = exchanges;
		tradeLogger.log(
						"Cross Trade. Coin: %s. Long: %s | Short: %s",
						coin,
						exchanges.longEx().name,
						exchanges.shortEx().name
		);
	}
}
