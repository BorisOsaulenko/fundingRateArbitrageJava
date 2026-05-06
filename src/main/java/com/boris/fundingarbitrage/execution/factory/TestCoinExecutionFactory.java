package com.boris.fundingarbitrage.execution.factory;

import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.execution.TestCoinExecution;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;

public class TestCoinExecutionFactory extends CoinExecutionFactory {
	@Override
	public CoinExecution create(
					String coin,
					CoinOpportunity op,
					ArbitrageBotConfig config
	) {
		return new TestCoinExecution(coin, op, config);
	}
}
