package com.boris.fundingarbitrage.scheduler;

import java.util.concurrent.TimeUnit;

public interface ModifiableSchedulerBuilder {
	ModifiableScheduler create(Runnable work, long initialFrequencyMs);

	ModifiableScheduler create(Runnable work, long frequency, TimeUnit timeUnit);
}
