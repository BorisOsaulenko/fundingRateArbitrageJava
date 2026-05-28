package com.boris.fundingarbitrage.scheduler.onetime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ProdOneTimeScheduler implements IOneTimeScheduler {
	private final Logger log = LoggerFactory.getLogger(ProdOneTimeScheduler.class);
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final List<ScheduledFuture<?>> runningTasks = new ArrayList<>();
	private volatile boolean shutdown = false;

	private void processWork(Runnable work) {
		try {
			work.run();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	@Override
	public void schedule(Runnable work, long delayMs) {
		if (shutdown) throw new IllegalStateException("Scheduler has been shutdown");
		Runnable scheduledWork = () -> processWork(work);
		ScheduledFuture<?> sf = scheduler.schedule(scheduledWork, delayMs, TimeUnit.MILLISECONDS);
		runningTasks.add(sf);
	}

	@Override
	public void schedule(Runnable work, long delayMs, TimeUnit unit) {
		schedule(work, unit.toMillis(delayMs));
	}

	@Override
	public void cancelAll() {
		runningTasks.forEach(t -> t.cancel(true));
	}

	@Override
	public void shutdown() {
		shutdown = true;
		cancelAll();
		scheduler.shutdown();
	}
}
