package com.boris.fundingarbitrage.exchange.publichttp;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;

import java.math.BigDecimal;

// Represents the data we should pull from public rest only once at the start of the program
public record FuturesPublicOnePullData(
				BigDecimal lotSize,
				BigDecimal volume24h,
				int fundingInterval,
				BookTicker ticker,
				Funding fundingRate,
				FuturesTradingState tradingState
) {
	public boolean isEmpty() {
		return ticker == null || fundingRate == null;
	}
}
