package com.boris.fundingarbitrage.util.logger;

import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.slf4j.Logger;

public class CoinVectorLogger {
	private CoinVectorLogger() {
	}

	public static <T> void logCoinVector(Logger log, CoinVector<T> coinVector) {
		if (coinVector == null) {
			log.info("(CoinVector) <null>");
			return;
		}

		if (coinVector.isEmpty()) {
			log.info("(CoinVector) <empty>");
			return;
		}

		int coinColWidth = "Coin".length();
		int valueColWidth = "Value".length();

		for (var e : coinVector.entrySet()) {
			String coin = e.getKey();
			String value = String.valueOf(e.getValue());
			coinColWidth = Math.max(coinColWidth, coin.length());
			valueColWidth = Math.max(valueColWidth, value.length());
		}

		String border = "+" + "-".repeat(coinColWidth + 2) + "+" + "-".repeat(valueColWidth + 2) + "+";

		log.info("(CoinVector) size={}", coinVector.size());
		log.info(border);
		log.info(String.format("| %-" + coinColWidth + "s | %-" + valueColWidth + "s |", "Coin", "Value"));
		log.info(border);

		for (var e : coinVector.entrySet()) {
			String coin = e.getKey();
			String value = String.valueOf(e.getValue());
			log.info(String.format("| %-" + coinColWidth + "s | %-" + valueColWidth + "s |", coin, value));
		}

		log.info(border);
	}
}
