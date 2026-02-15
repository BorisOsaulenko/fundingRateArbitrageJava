package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.model.contract.BookTicker;

// Represents the data we should pull from public rest once at the start of program
public record PublicOnePullData(
				double lotSize, BookTicker bookTicker, double volume24h, int fundingInterval
) {
	public PublicOnePullData {
		if (bookTicker == null) throw new IllegalArgumentException("Book ticker cannot be null.");
	}
}
