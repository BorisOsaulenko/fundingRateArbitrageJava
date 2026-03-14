package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;

public record ExchangeSnapshot(
				BookTicker bookTicker, FundingRate fundingRate, MarkPrice markPrice
) {
	@Override
	public BookTicker bookTicker() {
		return bookTicker;
	}

	@Override
	public FundingRate fundingRate() {
		return fundingRate;
	}

	@Override
	public MarkPrice markPrice() {
		return markPrice;
	}
}