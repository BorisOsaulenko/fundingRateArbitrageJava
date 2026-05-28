package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.scheduler.onetime.IOneTimeScheduler;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FakeOneTimeScheduler implements IOneTimeScheduler {
	@Getter private final List<HistoryItem> history = new ArrayList<>();
	private final List<Runnable> scheduledWorks = new ArrayList<>();
	@Getter private boolean isShutdown = false;

	@Override
	public void schedule(Runnable work, long delayMs) {
		schedule(work, delayMs, TimeUnit.MILLISECONDS);
	}

	@Override
	public void schedule(Runnable work, long delayMs, TimeUnit unit) {
		if (isShutdown) throw new IllegalStateException("Scheduler has been shutdown");
		history.add(new Schedule(delayMs, unit));
		scheduledWorks.add(work);
	}

	@Override
	public void cancelAll() {
		history.add(new CancelAll());
		scheduledWorks.clear();
	}

	@Override
	public void shutdown() {
		isShutdown = true;
		cancelAll();
		history.add(new Shutdown());
	}

	public void doRunAll() {
		while (!scheduledWorks.isEmpty()) {
			Runnable work = scheduledWorks.removeFirst();
			work.run();
		}
	}

	public sealed interface HistoryItem permits Schedule, CancelAll, Shutdown {
	}

	public record Schedule(long delay, TimeUnit unit) implements HistoryItem {
	}

	public record CancelAll() implements HistoryItem {
	}

	public record Shutdown() implements HistoryItem {
	}
}
