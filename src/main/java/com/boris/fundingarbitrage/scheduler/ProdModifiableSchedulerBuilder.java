package com.boris.fundingarbitrage.scheduler;

public class ProdModifiableSchedulerBuilder implements ModifiableSchedulerBuilder {
	@Override
	public ModifiableScheduler create(Runnable work, long initialFrequencyMs) {
		return new ModifiableScheduler(work, initialFrequencyMs);
	}
}
