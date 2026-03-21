package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class TradeLogger {
	private static final DateTimeFormatter fmt = DateTimeFormatter
					.ofPattern("yyyy_MM_dd-HH:mm:ss")
					.withZone(ZoneId.of("UTC"));
	private final Path logFilePath;

	public TradeLogger(String coin) {
		String path = String.format("logs/%s_%s.log", coin, fmt.format(Instant.now()));
		this.logFilePath = Path.of(path);
	}

	public void logEnter(ArbitrageData enterData) {

	}
}
