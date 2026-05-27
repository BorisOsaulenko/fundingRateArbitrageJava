package com.boris.fundingarbitrage.execution.factory;

import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.tradelogger.TradeSessionLogger;

public abstract class CoinExecutionFactory {
	public abstract CoinExecution create(
					String coin,
					CoinOpportunity op,
					ArbitrageBotConfig config,
					TradeSessionLogger tradeLogger
	);
}
