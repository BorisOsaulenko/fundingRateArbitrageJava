package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.gate.GateExchange;
import com.boris.fundingarbitrage.util.logger.Logger;

class App {
	static void main(String[] args) throws Exception {
		GateExchange exchange = new GateExchange();
		exchange.publicWsClient.subscribeFundingRates(
						"SOL", (_) -> {
							Logger.log("Received funding rate update for SOL");
						}
		);

		Thread.sleep(80000); // Keep the application running for 1 minute to receive messages
	}
}
