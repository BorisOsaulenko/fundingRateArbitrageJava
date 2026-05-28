package com.boris.fundingarbitrage.execution.factory;

import com.boris.fundingarbitrage.execution.ITradeExecution;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.tradelogger.ITradeSessionLogger;

public abstract class TradeExecutionFactory {
	public abstract ITradeExecution create(
					String coin,
					CoinOpportunity op,
					ArbitrageBotConfig config,
					ITradeSessionLogger tradeLogger
	);
}
