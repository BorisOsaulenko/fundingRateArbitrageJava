package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceExchange;

class App {
	static void main(String[] args) throws Exception {
		BinanceExchange exchange = new BinanceExchange();
		exchange.publicWsClient.subscribeBookTicker(
						"SOL", patch -> {
							System.out.println("Book Ticker Patch: " + patch);
						}
		);

		Thread.sleep(10000);
	}
}
