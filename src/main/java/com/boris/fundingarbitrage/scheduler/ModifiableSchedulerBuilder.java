package com.boris.fundingarbitrage.scheduler;

public interface ModifiableSchedulerBuilder {
	ModifiableScheduler create(Runnable work, long initialFrequencyMs);
}
