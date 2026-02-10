package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceExchange;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.util.concurrent.atomic.AtomicInteger;

class App {
	static void main(String[] args) throws Exception {
		AtomicInteger counter = new AtomicInteger();
		BinanceExchange exchange = new BinanceExchange();
		exchange.publicWsClient.subscribeBookTicker(
						"SOL", message -> {
							counter.getAndIncrement();
						}
		);

		Thread.sleep(60000); // Keep the application running for 1 minute to receive messages
		Logger.log("Current Ticker: " + counter.get());
	}
}
