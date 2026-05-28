package com.boris.fundingarbitrage.scheduler.modifiable;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class ModifiableScheduler implements IModifiableScheduler {
	private final Logger log = LoggerFactory.getLogger(ModifiableScheduler.class);
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final Runnable work;
	private volatile ScheduledFuture<?> runningTask;
	@Getter private volatile long currentFrequencyMs; // Can be updated mid-fly
	private volatile boolean shutdown = false;

	public ModifiableScheduler(Runnable work, long initialFrequencyMs) {
		this.work = work;
		this.currentFrequencyMs = initialFrequencyMs;
	}

	public ModifiableScheduler(Runnable work, long frequency, TimeUnit unit) {
		this.work = work;
		this.currentFrequencyMs = TimeUnit.MILLISECONDS.convert(frequency, unit);
	}

	@Override
	public void setFrequency(long newFrequencyMs) {
		this.currentFrequencyMs = newFrequencyMs;
	}

	@Override
	public void setFrequency(long freq, TimeUnit unit) {
		this.currentFrequencyMs = TimeUnit.MILLISECONDS.convert(freq, unit);
	}

	@Override
	public void cancelNow() {
		shutdown = true;
		if (runningTask != null) runningTask.cancel(true);
		scheduler.shutdownNow();
	}

	@Override
	public void start() {
		if (shutdown) return;

		try {
			work.run();
		} catch (Exception e) {
			log.error("Exception during processing internal tick: {}", e.getMessage());
			throw new RuntimeException(e);
		} finally {
			if (!shutdown) {
				runningTask = scheduler.schedule(this::start, currentFrequencyMs, TimeUnit.MILLISECONDS);
			}
		}
	}
}
