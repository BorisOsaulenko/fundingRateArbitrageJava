package com.boris.fundingarbitrage.util.logger;

import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class Logger {
	private static final int BATCH_LIMIT = 100;
	private static final long IDLE_FLUSH_NANOS = TimeUnit.SECONDS.toNanos(3);
	private static final Object bufferLock = new Object();
	private static final List<LogEntry> buffer = new ArrayList<>(BATCH_LIMIT);
	private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
	private static volatile long lastWriteNanos = System.nanoTime();
	private static boolean schedulerStarted = false;
	private static BufferedWriter writer;
	private static boolean initCalled = false;
	private static boolean includeStackTrace = false;

	public static void init(Path logFilePath) {
		if (initCalled) throw new IllegalStateException("Logger constructor called more than once");

		initCalled = true;
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
		System.out.println("Logger initialized. Log file: " +
											 (logFilePath != null ? logFilePath.toAbsolutePath() : "None (console only)"));
	}

	private static void enqueueLine(String line, boolean toErr) {
		startSchedulerIfNeeded();
		synchronized (bufferLock) {
			buffer.add(new LogEntry(line, toErr));
			lastWriteNanos = System.nanoTime();
			if (buffer.size() >= BATCH_LIMIT) {
				flushLocked();
			}
		}
	}

	private static void startSchedulerIfNeeded() {
		synchronized (bufferLock) {
			if (schedulerStarted) return;
			schedulerStarted = true;
			scheduler.scheduleWithFixedDelay(Logger::flushIfIdle, 1, 1, TimeUnit.SECONDS);
		}
	}

	private static void flushIfIdle() {
		long now = System.nanoTime();
		if (now - lastWriteNanos < IDLE_FLUSH_NANOS) return;
		synchronized (bufferLock) {
			if (System.nanoTime() - lastWriteNanos >= IDLE_FLUSH_NANOS) {
				flushLocked();
			}
		}
	}

	private static void flushLocked() {
		if (buffer.isEmpty()) return;
		if (writer == null) {
			for (LogEntry entry : buffer) {
				if (entry.toErr) System.err.println(entry.line);
				else System.out.println(entry.line);
			}
			System.out.flush();
			System.err.flush();
		} else {
			try {
				for (LogEntry entry : buffer) {
					writer.write(entry.line);
					writer.newLine();
				}
				writer.flush();
			} catch (IOException e) {
				throw new UncheckedIOException("Failed writing log lines to file", e);
			}
		}
		buffer.clear();
	}

	private static String getLogPrefix() {
		if (includeStackTrace) {
			List<StackTraceElement> stackTrace = Arrays.stream(Thread.currentThread().getStackTrace()).toList();
			StackTraceElement careAbout = stackTrace.getLast();
			return String.format("[%s] [%s] [%s] ", Thread.currentThread().getName(), Instant.now().toString(), careAbout);
		}

		return String.format("[%s] [%s]", Thread.currentThread().getName(), Instant.now().toString());
	}

	private static String formatMessage(String format, Object... args) {
		if (args == null || args.length == 0) return format;
		return String.format(format, args);
	}

	public static void log(Object message) {
		enqueueLine(getLogPrefix() + message, false);
	}

	public static void log(String format, Object... args) {
		enqueueLine(getLogPrefix() + formatMessage(format, args), false);
	}

	public static void warn(Object message) {
		enqueueLine(getLogPrefix() + message, false);
	}

	public static void warn(String format, Object... args) {
		enqueueLine(getLogPrefix() + formatMessage(format, args), false);
	}

	public static void error(Object message) {
		enqueueLine(getLogPrefix() + message, true);
	}

	public static void error(String format, Object... args) {
		enqueueLine(getLogPrefix() + formatMessage(format, args), true);
	}

	public static void debug(String message) {
		enqueueLine(getLogPrefix() + message, true);
	}

	public static void debug(String format, Object... args) {
		enqueueLine(getLogPrefix() + formatMessage(format, args), true);
	}

	public static <T> void logCoinVector(CoinVector<T> coinVector) {
		if (coinVector == null) {
			enqueueLine(getLogPrefix() + "(CoinVector) <null>", false);
			return;
		}

		if (coinVector.isEmpty()) {
			enqueueLine(getLogPrefix() + "(CoinVector) <empty>", false);
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

		enqueueLine(getLogPrefix() + "(CoinVector) size=" + coinVector.size(), false);
		enqueueLine(border, false);
		enqueueLine(String.format("| %-" + coinColWidth + "s | %-" + valueColWidth + "s |", "Coin", "Value"), false);
		enqueueLine(border, false);

		for (var e : coinVector.entrySet()) {
			String coin = e.getKey();
			String value = String.valueOf(e.getValue());
			enqueueLine(String.format("| %-" + coinColWidth + "s | %-" + valueColWidth + "s |", coin, value), false);
		}

		enqueueLine(border, false);
	}

	public synchronized static void closeLogFile() {
		synchronized (bufferLock) {
			flushLocked();
		}
		scheduler.shutdown();
		if (writer == null) return;

		try {
			writer.close();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to close log file", e);
		}
	}

	public void includeStackTrace() {
		Logger.includeStackTrace = true;
	}

	private static final class DaemonThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "logger-flush");
			t.setDaemon(true);
			return t;
		}
	}

	private record LogEntry(String line, boolean toErr) {
	}
}
