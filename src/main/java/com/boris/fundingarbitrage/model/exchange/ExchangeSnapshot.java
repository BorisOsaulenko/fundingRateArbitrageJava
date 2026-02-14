package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;

public class ExchangeSnapshot {
	public String exchangeName;
	public BookTicker bookTicker;
	public Fees fees;
	public FundingRate fundingRate;
	public MarkPrice markPrice;

	public ExchangeSnapshot(
					String exchangeName,
					BookTicker bookTicker,
					Fees fees,
					FundingRate fundingRate,
					MarkPrice markPrice
	) {
		this.exchangeName = exchangeName;
		this.bookTicker = bookTicker;
		this.fees = fees;
		this.fundingRate = fundingRate;
		this.markPrice = markPrice;
	}
}
