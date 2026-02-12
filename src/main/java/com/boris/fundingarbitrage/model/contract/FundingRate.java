package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;

import java.time.Instant;

public class FundingRate {
	public double rate;
	public Instant settlement;
	public Instant timestamp;

	public FundingRate(double rate, @NonNull Instant settlement, @NonNull Instant timestamp) {
		this.rate = rate;
		this.settlement = settlement;
		this.timestamp = timestamp;
	}

	public static FundingRate empty() {
		return new FundingRate(0, Instant.EPOCH, Instant.EPOCH);
	}

	@Override
	public String toString() {
		return "FundingRate{" +
				"rate=" + rate +
				", settlement=" + settlement +
				", timestamp=" + timestamp +
				'}';
	}
}
