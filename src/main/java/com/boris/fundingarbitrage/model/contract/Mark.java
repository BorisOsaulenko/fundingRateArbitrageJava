package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record Mark(BigDecimal price, @NonNull Instant timestamp) {
	public Mark {
		if (!Instant.EPOCH.equals(timestamp)) {
			Validations.requirePositive(price, "Price");
		}
	}

	public static Mark empty() {
		return new Mark(BigDecimal.ZERO, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(Mark markPrice) {
		return markPrice.price().equals(BigDecimal.ZERO) || Instant.EPOCH.equals(markPrice.timestamp());
	}
}
