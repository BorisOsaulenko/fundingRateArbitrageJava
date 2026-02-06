package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.bybit.BybitExchange;
import com.boris.fundingarbitrage.util.logger.Logger;

class App {
	static void main(String[] args) throws Exception {
		BybitExchange exchange = new BybitExchange();
		exchange.publicWsClient.subscribeFundingRates(
						"SOL", (_) -> {
							Logger.log("Received funding rate update for SOL");
						}
		);

		Thread.sleep(80000); // Keep the application running for 1 minute to receive messages
	}
}
