package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.model.contract.BookTicker;

import java.math.BigDecimal;

// Represents the data we should pull from public rest once at the start of program
public record PublicOnePullData(
				BigDecimal lotSize, BookTicker bookTicker, double volume24h, int fundingInterval
) {
	public PublicOnePullData {}

	public static PublicOnePullData empty() {
		return new PublicOnePullData(BigDecimal.ZERO, null, 0, 0);
	}

	public boolean isEmpty() {
		return bookTicker == null;
	}
}
