package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinExchange;
import com.boris.fundingarbitrage.util.logger.Logger;

class App {
	static void main(String[] args) throws Exception {
		KucoinExchange exchange = new KucoinExchange();
		exchange.publicWsClient.subscribeFundingRates(
						"SOL", (_) -> {
							Logger.log("Received funding rate update for SOL");
						}
		);

		Thread.sleep(80000); // Keep the application running for 1 minute to receive messages
	}
}
