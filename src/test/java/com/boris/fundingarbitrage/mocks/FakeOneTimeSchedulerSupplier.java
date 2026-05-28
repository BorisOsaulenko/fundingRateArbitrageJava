package com.boris.fundingarbitrage.mocks;

import com.boris.fundingarbitrage.scheduler.onetime.IOneTimeScheduler;
import com.boris.fundingarbitrage.scheduler.onetime.IOneTimeSchedulerSupplier;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class FakeOneTimeSchedulerSupplier implements IOneTimeSchedulerSupplier {
	@Getter
	private static final List<FakeOneTimeScheduler> createdInstances = new ArrayList<>();

	public static void refresh() {
		createdInstances.clear();
	}

	public static boolean allInstancesShutdown() {
		return createdInstances.stream().allMatch(FakeOneTimeScheduler::isShutdown);
	}

	@Override
	public IOneTimeScheduler get() {
		var scheduler = new FakeOneTimeScheduler();
		createdInstances.add(scheduler);
		return scheduler;
	}
}
