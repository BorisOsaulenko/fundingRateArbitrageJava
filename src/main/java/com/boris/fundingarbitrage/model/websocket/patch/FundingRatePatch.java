package com.boris.fundingarbitrage.model.websocket.patch;

import lombok.NonNull;

import java.time.Instant;

public record FundingRatePatch(
				@NonNull String coin, Double rate, Instant settlement, @NonNull Instant timestamp
) implements GenericPublicWsPatch {
	public FundingRatePatch {
		if (rate == null && settlement == null) {
			throw new IllegalArgumentException("Rate and settlement cannot both be absent.");
		}
	}
}
