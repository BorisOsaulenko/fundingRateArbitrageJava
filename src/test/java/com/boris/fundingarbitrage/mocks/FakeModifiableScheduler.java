package com.boris.fundingarbitrage.mocks;

import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableScheduler;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FakeModifiableScheduler implements IModifiableScheduler {
	@Getter private final List<HistoryItem> history = new ArrayList<>();
	private final Runnable work;
	@Getter private boolean isRunning = false;

	public FakeModifiableScheduler(Runnable work) {
		this.work = work;
	}

	boolean compareWork(Runnable work) {
		return this.work.equals(work);
	}

	@Override
	public void setFrequency(long freqMs) {
		history.add(new SetFrequency(freqMs, TimeUnit.MILLISECONDS));
	}

	@Override
	public void setFrequency(long freq, TimeUnit unit) {
		history.add(new SetFrequency(freq, unit));
	}

	@Override
	public void cancelNow() {
		isRunning = false;
		history.add(new Stop());
	}

	@Override
	public void start() {
		isRunning = true;
		history.add(new Start());
	}

	public void doRun() {
		work.run();
	}

	public sealed interface HistoryItem permits Start, Stop, SetFrequency {
	}

	public record Start() implements HistoryItem {
	}

	public record Stop() implements HistoryItem {
	}

	public record SetFrequency(long freq, TimeUnit unit) implements HistoryItem {
	}
}
