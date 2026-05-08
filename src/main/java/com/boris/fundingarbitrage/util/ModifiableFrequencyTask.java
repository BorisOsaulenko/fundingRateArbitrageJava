package com.boris.fundingarbitrage.util;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ModifiableFrequencyTask implements Runnable {
	private final static Logger log = LoggerFactory.getLogger(ModifiableFrequencyTask.class);
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
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
			log.error("Exception during processing internal tick: {}", e.getMessage());
			log.error(Arrays.toString(e.getStackTrace()));
			throw new RuntimeException(e);
		} finally {
			// Self-reschedule for the next iteration using the updated delay
			runningTask = scheduler.schedule(this, currentFrequencyMs, TimeUnit.MILLISECONDS);
		}
	}
}