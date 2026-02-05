package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.gate.GateExchange;
import com.boris.fundingarbitrage.util.logger.Logger;

class App {
	static void main(String[] args) throws Exception {
		GateExchange gateExchange = new GateExchange();
		gateExchange.publicWsClient.subscribeFundingRates(
						"SOL", data -> {
							Logger.getInstance().log("Mark Price Update: " + data);
						}
		);

		Thread.sleep(60000); // Keep the application running for 1 minute to receive messages
	}
}
