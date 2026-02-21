package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;

import java.time.Instant;

public record FundingRate(
				double rate, @NonNull Instant settlement, @NonNull Instant timestamp
) {
	private static final double emptyRateValue = -10; // funding rates are usually between -0.1% and 0.1%, so -10% is a safe "empty" value

	public static FundingRate empty() {
		return new FundingRate(emptyRateValue, Instant.EPOCH, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(FundingRate fundingRate) {
		return fundingRate.rate() == emptyRateValue ||
					 fundingRate.settlement() == null ||
					 Instant.EPOCH.equals(fundingRate.settlement()) ||
					 fundingRate.timestamp() == null ||
					 Instant.EPOCH.equals(fundingRate.timestamp());
	}
}
