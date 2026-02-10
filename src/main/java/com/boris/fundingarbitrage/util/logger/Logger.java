package com.boris.fundingarbitrage.util.logger;

import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Logger {
	private static BufferedWriter writer;
	private static Logger INSTANCE = new Logger(null);
	private static boolean initCalled = false;

	private Logger(Path logFilePath) {
		if (logFilePath != null) {
			try {
				writer = Files.newBufferedWriter(
								logFilePath,
								StandardCharsets.UTF_8,
								StandardOpenOption.CREATE,
								StandardOpenOption.APPEND,
								StandardOpenOption.WRITE
				);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to open log file: " + logFilePath, e);
			}
		} else {
			writer = null;
		}
	}

	public static void init(Path logFilePath) {
		if (initCalled) {
			throw new IllegalStateException("Logger constructor called more than once");
		}
		INSTANCE = new Logger(logFilePath);
		initCalled = true;
	}

	private static void writeLine(String line) {
		if (writer == null) {
			System.out.println(line);
			return;
		}

		try {
			writer.write(line);
			writer.newLine();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed writing log line to file", e);
		}
	}

	private static String getLogPrefix(String type) {
		return String.format("[%s] [%s] ", Thread.currentThread().getName(), type);
	}

	public static void log(String message) {
		writeLine(getLogPrefix("INFO") + message);
	}

	public static void warn(String message) {
		writeLine(getLogPrefix("WARN") + message);
	}

	public static void error(String message) {
		writeLine(getLogPrefix("ERROR") + message);
	}

	public static void debug(String message) {
		String line = getLogPrefix("Debug") + message;
		if (writer == null) {
			System.err.println(line);
			System.err.flush();
			return;
		}
		try {
			writer.write(line);
			writer.newLine();
			writer.flush(); // force write-through
		} catch (IOException e) {
			throw new UncheckedIOException("Failed writing critical log line", e);
		}
	}

	public static <T> void logCoinVector(CoinVector<T> coinVector) {
		if (coinVector == null) {
			writeLine(getLogPrefix("INFO") + "(CoinVector) <null>");
			return;
		}

		if (coinVector.isEmpty()) {
			writeLine(getLogPrefix("INFO") + "(CoinVector) <empty>");
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

		writeLine(getLogPrefix("INFO") + "(CoinVector)");
		writeLine(border);
		writeLine(String.format("| %-" + coinColWidth + "s | %-" + valueColWidth + "s |", "Coin", "Value"));
		writeLine(border);

		for (var e : coinVector.entrySet()) {
			String coin = e.getKey();
			String value = String.valueOf(e.getValue());
			writeLine(String.format("| %-" + coinColWidth + "s | %-" + valueColWidth + "s |", coin, value));
		}

		writeLine(border);
	}

	public synchronized void closeLogFile() {
		if (writer == null) return;

		try {
			writer.close();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to close log file", e);
		}
	}
}
