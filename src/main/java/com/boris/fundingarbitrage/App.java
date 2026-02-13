package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceExchange;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class App {
	static List<String> coins = List.of(
					"ETH",
					"BNB",
					"XRP",
					"SOL",
					"ADA",
					"DOGE",
					"TRX",
					"DOT",
					"LTC",
					"BCH",
					"LINK",
					"XLM",
					"AVAX",
					"ATOM",
					"ETC",
					"UNI",
					"ICP",
					"HBAR"
	);

	static void main(String[] args) throws Exception {
		CoinMonitor monitor = new CoinMonitor(coins);
		Logger.init(Path.of("app.log"));
		Logger.log("Initializing coin monitor and waiting for complete data...");

		try {
			monitor.getInitFuture().get();
			Logger.log("Coin monitor initialized successfully with complete data. Starting arbitrage logic...");
		} catch (Exception e) {
			Logger.error("Failed to initialize coin monitor: " + e.getMessage());
			throw new RuntimeException("Coin monitor initialization failed", e);
		} finally {
			monitor.shutdown();
		}

//		Thread.sleep(40000);
	}

	static void main2(String[] args) throws ExecutionException, InterruptedException {
		BinanceExchange gate = new BinanceExchange();
		gate.privateHttpClient.getTradingFees("KAITO").thenAccept(fees -> {
			System.out.println("Maker fee: " + fees.closeMaker);
			System.out.println("Taker fee: " + fees.closeTaker);
		}).get();

		Thread.sleep(30000);
	}
}
