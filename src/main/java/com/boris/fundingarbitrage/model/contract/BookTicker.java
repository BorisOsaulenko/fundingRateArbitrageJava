package com.boris.fundingarbitrage.model.contract;

import java.time.Instant;

public record BookTicker(
				double bidPrice, double bidSize, double askPrice, double askSize, Instant timestamp
) {

	public static BookTicker empty() {
		return new BookTicker(0, 0, 0, 0, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(BookTicker bookTicker) {
		return bookTicker.bidPrice() == 0 ||
					 bookTicker.askPrice() == 0 ||
					 bookTicker.bidSize() == 0 ||
					 bookTicker.askSize() == 0 ||
					 bookTicker.timestamp() == null ||
					 Instant.EPOCH.equals(bookTicker.timestamp());
	}
}
