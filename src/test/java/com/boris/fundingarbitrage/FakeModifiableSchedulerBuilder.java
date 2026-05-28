package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableSchedulerBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FakeModifiableSchedulerBuilder implements IModifiableSchedulerBuilder {
	@Getter
	private static final List<FakeModifiableScheduler> createdInstances = new ArrayList<>();

	public static void refresh() {
		createdInstances.clear();
	}

	public static boolean allInstancesStarted() {
		return createdInstances.stream().allMatch(FakeModifiableScheduler::isRunning);
	}

	public static boolean existsInstanceWithWork(Runnable work) {
		return createdInstances.stream().anyMatch(inst -> inst.compareWork(work));
	}

	@Override
	public IModifiableScheduler create(Runnable work, long initialFrequencyMs) {
		var scheduler = new FakeModifiableScheduler(work);
		createdInstances.add(scheduler);
		return scheduler;
	}

	@Override
	public IModifiableScheduler create(Runnable work, long frequency, TimeUnit timeUnit) {
		return create(work, 0);
	}
}

