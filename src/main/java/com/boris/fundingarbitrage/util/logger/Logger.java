package com.boris.fundingarbitrage.util.logger;

import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Logger implements ILogger {
	private static Logger INSTANCE = new Logger(null);
	private static boolean initCalled = false;
	private final BufferedWriter writer;

	private Logger(Path logFilePath) {
		if (logFilePath != null) {
			try {
				this.writer = Files.newBufferedWriter(
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
			this.writer = null;
		}
	}

	public static void instantiate(Path logFilePath) {
		if (initCalled) {
			throw new IllegalStateException("Logger constructor called more than once");
		}
		INSTANCE = new Logger(logFilePath);
		initCalled = true;
	}

	public static Logger getInstance() {
		return INSTANCE;
	}

	public synchronized void closeLogFile() {
		if (this.writer == null) return;

		try {
			this.writer.close();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to close log file", e);
		}
	}

	private void writeLine(String line) {
		if (this.writer == null) {
			System.out.println(line);
			return;
		}

		try {
			this.writer.write(line);
			this.writer.newLine();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed writing log line to file", e);
		}
	}

	private String getLogPrefix(String type) {
		return String.format("[%s] [%s] ", Thread.currentThread().getName(), type);
	}

	@Override
	public void log(String message) {
		writeLine(getLogPrefix("INFO") + message);
	}

	@Override
	public void warn(String message) {
		writeLine(getLogPrefix("WARN") + message);
	}

	@Override
	public void error(String message) {
		writeLine(getLogPrefix("ERROR") + message);
	}
	
	public void debug(String message) {
		String line = getLogPrefix("Debug") + message;
		if (this.writer == null) {
			System.err.println(line);
			System.err.flush();
			return;
		}
		try {
			this.writer.write(line);
			this.writer.newLine();
			this.writer.flush(); // force write-through
		} catch (IOException e) {
			throw new UncheckedIOException("Failed writing critical log line", e);
		}
	}


	@Override
	public <T> void logCoinVector(CoinVector<T> coinVector) {
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
		writeLine(String.format(
						"| %-" + coinColWidth + "s | %-" + valueColWidth + "s |",
						"Coin",
						"Value"
		));
		writeLine(border);

		for (var e : coinVector.entrySet()) {
			String coin = e.getKey();
			String value = String.valueOf(e.getValue());
			writeLine(String.format(
							"| %-" + coinColWidth + "s | %-" + valueColWidth + "s |",
							coin,
							value
			));
		}

		writeLine(border);
	}
}
