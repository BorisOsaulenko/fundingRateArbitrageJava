package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceExchange;
import com.boris.fundingarbitrage.monitor.CoinMonitor;
import com.boris.fundingarbitrage.util.logger.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class App {
//	static List<String> coins = List.of(
//					"ETH",
//					"SOL",
//					"WIF",
//					"PEPE",
//					"DOGE",
//					"XRP",
//					"0G",
//					"1INCH",
//					"1MBABYDOGE",
//					"2Z",
//					"4",
//					"A2Z",
//					"AAVE",
//					"ACE",
//					"ACH",
//					"ACTSOL",
//					"ACU",
//					"ADA",
//					"AERGO",
//					"AERO",
//					"AEVO",
//					"AGI",
//					"AGLD",
//					"AGT",
//					"AIN",
//					"AIOT",
//					"AIO",
//					"AI",
//					"AIXBT",
//					"AKE",
//					"ALCH",
//					"ALGO",
//					"ALICE",
//					"ALLO",
//					"ALPINE",
//					"ALT",
//					"ALU",
//					"ANIME",
//					"ANKR",
//					"APE",
//					"API3",
//					"APR",
//					"APT",
//					"ARB",
//					"ARC",
//					"ARIA",
//					"ARKM",
//					"ARK",
//					"ARPA",
//					"AR",
//					"ASP",
//					"ASR",
//					"ASTER",
//					"ASTR",
//					"ATH",
//					"ATOM",
//					"AT",
//					"AUCTION",
//					"AUDIO",
//					"A",
//					"AVAAI",
//					"AVA",
//					"AVAX",
//					"AVNT",
//					"AWE",
//					"AXL",
//					"AXS",
//					"AZTEC",
//					"B2",
//					"B3",
//					"BABY",
//					"BANANAS31",
//					"BANANA",
//					"BAND",
//					"BANK",
//					"BAN",
//					"BARD",
//					"BAS",
//					"BAT",
//					"BB",
//					"BCH",
//					"BDXN",
//					"BDX",
//					"BEAT",
//					"BEL",
//					"BERA",
//					"BIGTIME",
//					"BINANCELIFE",
//					"BIO",
//					"BIRB",
//					"BLAST",
//					"BLESS",
//					"BLUAI" // 92 coins
//	);

	private static final List<String> coins = List.of("SOL", "ETH", "XRP", "LTC", "ADA");

	static void main2(String[] args) throws Exception {
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

	static void main(String[] args) throws ExecutionException, InterruptedException {
		Logger.init(Path.of("app.log"));
		BinanceExchange binance = new BinanceExchange();
		var result = binance.publicHttpClient.getOnePullData(coins).get();
	}
}
