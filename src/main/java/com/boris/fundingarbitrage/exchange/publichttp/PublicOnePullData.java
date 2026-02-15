package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.model.contract.BookTicker;

// Represents the data we should pull from public rest once at the start of program
public record PublicOnePullData(
				double lotSize, BookTicker bookTicker, double volume24h, int fundingGranularityHours
) {
	public PublicOnePullData(double lotSize, BookTicker bookTicker, double volume24h, int fundingGranularityHours) {
		if (bookTicker == null) throw new IllegalArgumentException("Book ticker cannot be null.");
		this.lotSize = lotSize;
		this.bookTicker = bookTicker;
		this.volume24h = volume24h;
		this.fundingGranularityHours = fundingGranularityHours;
	}
}
