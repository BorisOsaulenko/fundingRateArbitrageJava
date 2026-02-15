package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.model.contract.BookTicker;

// Represents the data we should pull from public rest once at the start of program
public record PublicOnePullData(
				double lotSize, BookTicker bookTicker, Double volume24h, int fundingGranularityHours
) {}
