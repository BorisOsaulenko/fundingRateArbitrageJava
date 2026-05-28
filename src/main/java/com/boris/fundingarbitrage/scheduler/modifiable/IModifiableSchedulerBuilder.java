package com.boris.fundingarbitrage.scheduler.modifiable;

import java.util.concurrent.TimeUnit;

public interface IModifiableSchedulerBuilder {
	IModifiableScheduler create(Runnable work, long initialFrequencyMs);

	IModifiableScheduler create(Runnable work, long frequency, TimeUnit timeUnit);
}
