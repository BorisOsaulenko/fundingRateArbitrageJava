package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record BookTicker(
				BigDecimal bidPrice, BigDecimal bidSize, BigDecimal askPrice, BigDecimal askSize, @NonNull Instant timestamp
) {

	public static BookTicker empty() {
		return new BookTicker(null, null, null, null, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(BookTicker bookTicker) {
		return bookTicker.bidPrice() == null ||
					 bookTicker.askPrice() == null ||
					 bookTicker.bidSize() == null ||
					 bookTicker.askSize() == null ||
					 Instant.EPOCH.equals(bookTicker.timestamp());
	}
}
