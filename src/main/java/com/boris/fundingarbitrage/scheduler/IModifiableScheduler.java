package com.boris.fundingarbitrage.scheduler;

import java.util.concurrent.TimeUnit;

public interface IModifiableScheduler {
	void setFrequency(long freqMs);

	void setFrequency(long freq, TimeUnit unit);

	void cancelNow();

	void start();
}
