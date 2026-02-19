package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.model.contract.BookTicker;

// Represents the data we should pull from public rest once at the start of program
public record PublicOnePullData(
				double lotSize, BookTicker bookTicker, double volume24h, int fundingInterval
) {
	public PublicOnePullData {}

	public static PublicOnePullData empty() {
		return new PublicOnePullData(0, null, 0, 0);
	}

	public boolean isEmpty() {
		return bookTicker == null;
	}
}
