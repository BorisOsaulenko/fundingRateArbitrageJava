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
}
