package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;

public record FuturesSnapshot(
				BookTicker bookTicker,
				FundingRate fundingRate,
				MarkPrice markPrice
) {
}