package com.boris.fundingarbitrage.execution.factory;

import com.boris.fundingarbitrage.execution.ClassicCoinExecution;
import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.model.assetops.Leverages;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import com.boris.fundingarbitrage.strategy.pretradestrategy.TradeDirections;

public class ClassicCoinExecutionFactory extends CoinExecutionFactory {
	@Override
	public CoinExecution create(
					String coin,
					ExchangePair exchanges,
					TradeParams tradeParams,
					Leverages leverages,
					TradeDirections tradeDirections
	) {
		return new ClassicCoinExecution(coin, exchanges, tradeParams, leverages, tradeDirections);
	}
}
