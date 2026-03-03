package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record MarkPrice(BigDecimal price, @NonNull Instant timestamp) {
	public MarkPrice {
		if (!Instant.EPOCH.equals(timestamp)) {
			Validations.requirePositive(price, "Price");
		}
	}

	public static MarkPrice empty() {
		return new MarkPrice(BigDecimal.ZERO, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(MarkPrice markPrice) {
		return markPrice.price().equals(BigDecimal.ZERO) || Instant.EPOCH.equals(markPrice.timestamp());
	}
}
