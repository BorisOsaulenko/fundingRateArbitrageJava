package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceExchange;
import com.boris.fundingarbitrage.util.logger.Logger;

class App {
	static void main(String[] args) throws Exception {
		BinanceExchange exchange = new BinanceExchange();
		exchange.publicWsClient.subscribeMarkPrice(
						"SOL", (data) -> {
							Logger.log(data.toString());
						}
		);

		Thread.sleep(80000); // Keep the application running for 1 minute to receive messages
	}
}
