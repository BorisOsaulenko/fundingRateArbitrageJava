package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingRate(
				BigDecimal rate, @NonNull Instant settlement, @NonNull Instant timestamp
) {
	private static final BigDecimal emptyRateValue = BigDecimal.valueOf(-10);
	// funding rates are usually between -0.1% and 0.1%, so -10% is a safe "empty" value

	public static FundingRate empty() {
		return new FundingRate(emptyRateValue, Instant.EPOCH, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(FundingRate fundingRate) {
		return emptyRateValue.equals(fundingRate.rate()) ||
					 Instant.EPOCH.equals(fundingRate.settlement()) ||
					 Instant.EPOCH.equals(fundingRate.timestamp());
	}
}
