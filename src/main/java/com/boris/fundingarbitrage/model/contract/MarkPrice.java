package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

import java.time.Instant;

public record MarkPrice(double price, @NonNull Instant timestamp) {
	public MarkPrice {
		if (!Instant.EPOCH.equals(timestamp)) {
			Validations.requirePositive(price, "Price");
		}
	}

	public static MarkPrice empty() {
		return new MarkPrice(0, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(MarkPrice markPrice) {
		return markPrice.price() == 0 ||
					 markPrice.timestamp() == null ||
					 Instant.EPOCH.equals(markPrice.timestamp());
	}
}
