package com.boris.fundingarbitrage.logic.factory;

import com.boris.fundingarbitrage.execution.factory.TradeExecutionFactory;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.logic.TradeSession;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;

public class TradeSessionFactory {
	private final CoinMonitor monitor;
	private final InTradeStrategyFactory inTradeStrategyFactory;
	private final TradeExecutionFactory executionFactory;
	private final ArbitrageBotConfig config;
	private final IModifiableSchedulerBuilder schedulerBuilder;

	public TradeSessionFactory(
					CoinMonitor monitor,
					InTradeStrategyFactory inTradeStrategyFactory,
					TradeExecutionFactory executionFactory,
					ArbitrageBotConfig config,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		this.monitor = monitor;
		this.inTradeStrategyFactory = inTradeStrategyFactory;
		this.executionFactory = executionFactory;
		this.config = config;
		this.schedulerBuilder = schedulerBuilder;
	}

	public TradeSession create(String coin, CoinOpportunity opportunity) {
		return new TradeSession(
						coin,
						opportunity,
						config,
						monitor,
						inTradeStrategyFactory.create(opportunity),
						executionFactory,
						schedulerBuilder
		);
	}
}
