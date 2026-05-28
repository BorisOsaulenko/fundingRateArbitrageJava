package com.boris.fundingarbitrage.execution.factory;

import com.boris.fundingarbitrage.execution.ITradeExecution;
import com.boris.fundingarbitrage.execution.TradeExecution;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.tradelogger.ITradeSessionLogger;

public class ClassicTradeExecutionFactory extends TradeExecutionFactory {
	@Override
	public ITradeExecution create(
					String coin,
					CoinOpportunity op,
					ArbitrageBotConfig config,
					ITradeSessionLogger tradeLogger
	) {
		return new TradeExecution(coin, op, config, tradeLogger);
	}
}
