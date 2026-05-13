package com.boris.fundingarbitrage.scheduler;

import java.util.concurrent.TimeUnit;

public class ProdModifiableSchedulerBuilder implements ModifiableSchedulerBuilder {
	@Override
	public ModifiableScheduler create(Runnable work, long initialFrequencyMs) {
		return new ModifiableScheduler(work, initialFrequencyMs);
	}

	@Override
	public ModifiableScheduler create(Runnable work, long frequency, TimeUnit timeUnit) {
		return new ModifiableScheduler(work, frequency, timeUnit);
	}
}
