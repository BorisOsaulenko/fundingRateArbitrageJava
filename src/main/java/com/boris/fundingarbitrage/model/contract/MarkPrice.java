package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;
import lombok.NonNull;

import java.time.Instant;

public class MarkPrice {
	public double price;
	public Instant timestamp;

	public MarkPrice(double price, @NonNull Instant timestamp) {
		Validations.requirePositive(price, "Price");
	}

	private MarkPrice(double price) { // for empty constructor
		this.price = price;
		this.timestamp = Instant.EPOCH;
	}

	public static MarkPrice empty() {
		return new MarkPrice(0);
	}

	@Override
	public String toString() {
		return "MarkPrice{" +
				"price=" + price +
				", timestamp=" + timestamp +
				'}';
	}
}
