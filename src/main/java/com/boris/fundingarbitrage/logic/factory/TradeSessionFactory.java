package com.boris.fundingarbitrage.logic.factory;

import com.boris.fundingarbitrage.execution.factory.TradeExecutionFactory;
import com.boris.fundingarbitrage.logic.ArbitrageBotConfig;
import com.boris.fundingarbitrage.logic.CoinOpportunity;
import com.boris.fundingarbitrage.logic.TradeSession;
import com.boris.fundingarbitrage.monitor.ICoinMonitor;
import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.scheduler.onetime.IOneTimeSchedulerSupplier;
import com.boris.fundingarbitrage.tradelogger.TradeSessionLoggerBuilder;
import com.boris.fundingarbitrage.strategy.intradestrategy.factory.InTradeStrategyFactory;

public class TradeSessionFactory {
	private final ICoinMonitor monitor;
	private final InTradeStrategyFactory inTradeStrategyFactory;
	private final TradeExecutionFactory executionFactory;
	private final ArbitrageBotConfig config;
	private final IModifiableSchedulerBuilder schedulerBuilder;
	private final IOneTimeSchedulerSupplier oneTimeSchedulerSupplier;
	private final TradeSessionLoggerBuilder tradeLoggerBuilder;

	public TradeSessionFactory(
					ICoinMonitor monitor,
					InTradeStrategyFactory inTradeStrategyFactory,
					TradeExecutionFactory executionFactory,
					ArbitrageBotConfig config,
					IModifiableSchedulerBuilder modifiableSchedulerBuilder,
					IOneTimeSchedulerSupplier oneTimeSchedulerSupplier,
					TradeSessionLoggerBuilder tradeLoggerBuilder
	) {
		this.monitor = monitor;
		this.inTradeStrategyFactory = inTradeStrategyFactory;
		this.executionFactory = executionFactory;
		this.config = config;
		this.schedulerBuilder = modifiableSchedulerBuilder;
		this.oneTimeSchedulerSupplier = oneTimeSchedulerSupplier;
		this.tradeLoggerBuilder = tradeLoggerBuilder;
	}

	public TradeSession create(String coin, CoinOpportunity opportunity) {
		return new TradeSession(
						coin,
						opportunity,
						config,
						monitor,
						inTradeStrategyFactory.create(opportunity),
						executionFactory,
						schedulerBuilder,
						oneTimeSchedulerSupplier,
						tradeLoggerBuilder
		);
	}
}
