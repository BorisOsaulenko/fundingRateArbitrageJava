package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;

import java.math.BigDecimal;

// Represents the data we should pull from public rest only once at the start of the program
public record PublicOnePullData(
				BigDecimal lotSize,
				BigDecimal volume24h,
				int fundingInterval,
				BookTicker ticker,
				FundingRate fundingRate
) {
	public PublicOnePullData {
	}

	public static PublicOnePullData empty() {
		return new PublicOnePullData(BigDecimal.ZERO, BigDecimal.ZERO, 0, null, null);
	}

	public boolean isEmpty() {
		return ticker == null || fundingRate == null;
	}
}
