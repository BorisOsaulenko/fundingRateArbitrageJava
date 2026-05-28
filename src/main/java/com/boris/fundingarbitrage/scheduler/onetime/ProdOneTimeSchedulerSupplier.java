package com.boris.fundingarbitrage.scheduler.onetime;

public class ProdOneTimeSchedulerSupplier implements IOneTimeSchedulerSupplier {
	@Override
	public IOneTimeScheduler get() {
		return new ProdOneTimeScheduler();
	}
}
