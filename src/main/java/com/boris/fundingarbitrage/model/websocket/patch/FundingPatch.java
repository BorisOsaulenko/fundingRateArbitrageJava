package com.boris.fundingarbitrage.model.websocket.patch;

import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingPatch(
				@NonNull String coin, BigDecimal rate, Instant settlement, @NonNull Instant timestamp
) implements GenericPublicWsPatch {
	public FundingPatch {
		if (rate == null && settlement == null) {
			throw new IllegalArgumentException("Rate and settlement cannot both be absent.");
		}
	}
}
