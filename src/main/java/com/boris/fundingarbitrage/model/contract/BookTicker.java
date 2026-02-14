package com.boris.fundingarbitrage.model.contract;

import java.time.Instant;

public class BookTicker {
	public double bidPrice;
	public double bidSize;
	public double askPrice;
	public double askSize;
	public Instant timestamp;

	public BookTicker(double bidPrice, double bidSize, double askPrice, double askSize, Instant timestamp) {
		this.bidPrice = bidPrice;
		this.bidSize = bidSize;
		this.askPrice = askPrice;
		this.askSize = askSize;
		this.timestamp = timestamp;
	}

	public static BookTicker empty() {
		return new BookTicker(0, 0, 0, 0, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(BookTicker bookTicker) {
		return bookTicker.bidPrice == 0 ||
					 bookTicker.askPrice == 0 ||
					 bookTicker.bidSize == 0 ||
					 bookTicker.askSize == 0 ||
					 Instant.EPOCH.equals(bookTicker.timestamp);
	}

	@Override
	public String toString() {
		return "BookTicker{" +
					 "bidPrice=" +
					 bidPrice +
					 ", bidSize=" +
					 bidSize +
					 ", askPrice=" +
					 askPrice +
					 ", askSize=" +
					 askSize +
					 ", timestamp=" +
					 timestamp +
					 '}';
	}
}
