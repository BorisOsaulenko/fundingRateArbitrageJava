package com.boris.fundingarbitrage.logic;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TradeLogger {
	private static final DateTimeFormatter fmt = DateTimeFormatter
					.ofPattern("yyyy_MM_dd-HH:mm:ss")
					.withZone(ZoneId.of("UTC"));
	private final Path logFilePath;
	private final BufferedWriter writer;

	public TradeLogger(String coin) {
		String path = String.format("logs/%s_%s.log", coin, fmt.format(Instant.now()));
		this.logFilePath = Path.of(path);
		OutputStream out;
		try {
			Path parent = this.logFilePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			out = Files.newOutputStream(this.logFilePath);
		} catch (Exception e) {
			out = System.out;
		}
		this.writer = new BufferedWriter(new OutputStreamWriter(out));
	}

	String getPrefix(String type) {
		return "[" + type.toUpperCase() + "] [" + fmt.format(Instant.now()) + "] ";
	}

	public void log(Object message) {
		try {
			writer.write(getPrefix("LOG") + message.toString() + "\n");
			writer.flush();
		} catch (Exception e) {
			System.out.println("Failed to log trade message: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void warn(Object message) {
		try {
			writer.write(getPrefix("WARN") + message.toString() + "\n");
			writer.flush();
		} catch (Exception e) {
			System.out.println("Failed to log trade message: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void error(Object message) {
		try {
			writer.write(getPrefix("ERROR") + message.toString() + "\n");
			writer.flush();
		} catch (Exception e) {
			System.out.println("Failed to log trade message: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void log(String message, Object... args) {
		log(String.format(message, args));
	}

	public void warn(String message, Object... args) {
		warn(String.format(message, args));
	}

	public void error(String message, Object... args) {
		error(String.format(message, args));
	}
}
