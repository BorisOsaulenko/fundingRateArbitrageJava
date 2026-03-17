package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ModifiableFrequencyTask implements Runnable {
	private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final Runnable work;
	private volatile ScheduledFuture<?> runningTask;
	@Getter private volatile long currentFrequencyMs; // Can be updated mid-fly
	private volatile boolean shutdown = false;

	public ModifiableFrequencyTask(Runnable work, long initialFrequencyMs) {
		this.work = work;
		this.currentFrequencyMs = initialFrequencyMs;
	}

	public void setFrequency(long newFrequencyMs) {
		this.currentFrequencyMs = newFrequencyMs;
	}

	public void cancelNow() {
		shutdown = true;
		if (runningTask != null) runningTask.cancel(true);
		scheduler.shutdownNow();
	}

	@Override
	public void run() {
		if (shutdown) return;

		try {
			work.run();
		} catch (Exception e) {
			Logger.error("Exception during processing internal tick: " + e.getMessage());
			Logger.error(Arrays.toString(e.getStackTrace()));
			throw new RuntimeException(e);
		} finally {
			// Self-reschedule for the next iteration using the updated delay
			runningTask = scheduler.schedule(this, currentFrequencyMs, TimeUnit.MILLISECONDS);
		}
	}
}