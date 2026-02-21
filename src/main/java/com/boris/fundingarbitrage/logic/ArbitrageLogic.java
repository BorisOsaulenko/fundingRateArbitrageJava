package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.coinfilter.CoinFilter;
import com.boris.fundingarbitrage.coinfilter.CoinFilterConfig;
import com.boris.fundingarbitrage.coinfilter.CoinFilterResult;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.strategy.ArbitrageStrategy;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ArbitrageLogic {
	private final ArbitrageStrategy strategy;
	private final ArbitrageBotConfig config;
	private final CoinMonitor monitor;
	private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(6);

	public ArbitrageLogic(ArbitrageStrategy strategy, ArbitrageBotConfig arbConfig, CoinFilterConfig filterConfig) {
		this.strategy = strategy;
		this.config = arbConfig;

		CoinFilter filter = new CoinFilter(arbConfig.coins(), filterConfig);
		CoinFilterResult filtered = filter.filterSync();

		this.monitor = new CoinMonitor(filtered);
	}

	private void computeBestArbSnapshotForCoin(String coin) {

	}

	public void start() {

	}
}
