package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;

import java.time.Instant;

public class FundingRate {
	private static final double emptyRateValue = -10; // funding rates are usually between -0.1% and 0.1%, so -10% is a safe "empty" value
	public double rate;
	public Instant settlement;
	public Instant timestamp;

	public FundingRate(double rate, @NonNull Instant settlement, @NonNull Instant timestamp) {
		this.rate = rate;
		this.settlement = settlement;
		this.timestamp = timestamp;
	}

	public static FundingRate empty() {
		return new FundingRate(emptyRateValue, Instant.EPOCH, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(FundingRate fundingRate) {
		return fundingRate.rate == emptyRateValue ||
					 Instant.EPOCH.equals(fundingRate.settlement) ||
					 Instant.EPOCH.equals(fundingRate.timestamp);
	}


	@Override
	public String toString() {
		return "FundingRate{" + "rate=" + rate + ", settlement=" + settlement + ", timestamp=" + timestamp + '}';
	}
}
