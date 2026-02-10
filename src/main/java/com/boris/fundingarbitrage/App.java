package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.okx.OkxExchange;
import com.boris.fundingarbitrage.util.logger.Logger;

class App {
	static void main(String[] args) throws Exception {
		OkxExchange exchange = new OkxExchange();
		exchange.privateHttpClient.getFuturesUsdtBalance().thenApply(balance -> {
			Logger.log("USDT balance: " + balance);
			return null;
		});

		Thread.sleep(80000); // Keep the application running for 1 minute to receive messages
	}
}
