package com.boris.fundingarbitrage.scheduler;

import java.util.concurrent.TimeUnit;

public class ProdModifiableSchedulerBuilder implements IModifiableSchedulerBuilder {
	@Override
	public IModifiableScheduler create(Runnable work, long initialFrequencyMs) {
		return new ModifiableScheduler(work, initialFrequencyMs);
	}

	@Override
	public IModifiableScheduler create(Runnable work, long frequency, TimeUnit timeUnit) {
		return new ModifiableScheduler(work, frequency, timeUnit);
	}
}
