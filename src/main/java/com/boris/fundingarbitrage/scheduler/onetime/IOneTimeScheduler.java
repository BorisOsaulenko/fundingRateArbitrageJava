package com.boris.fundingarbitrage.scheduler.onetime;

import java.util.concurrent.TimeUnit;

public interface IOneTimeScheduler {
	void schedule(Runnable work, long delayMs);

	void schedule(Runnable work, long delayMs, TimeUnit unit);

	void cancelAll();

	void shutdown();
}
