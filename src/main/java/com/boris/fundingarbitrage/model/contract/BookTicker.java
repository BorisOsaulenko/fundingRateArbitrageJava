package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record BookTicker(
				BigDecimal bidPrice, BigDecimal bidSize, BigDecimal askPrice, BigDecimal askSize, @NonNull Instant timestamp
) {

	public static BookTicker empty() {
		return new BookTicker(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(BookTicker bookTicker) {
		return bookTicker.bidPrice().equals(BigDecimal.ZERO) ||
					 bookTicker.askPrice().equals(BigDecimal.ZERO) ||
					 bookTicker.bidSize().equals(BigDecimal.ZERO) ||
					 bookTicker.askSize().equals(BigDecimal.ZERO) ||
					 Instant.EPOCH.equals(bookTicker.timestamp());
	}
}
