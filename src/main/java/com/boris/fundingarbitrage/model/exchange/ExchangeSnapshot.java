package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.strategy.TradeMarket;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeSnapshot(
				FuturesSnapshot futuresSnapshot,
				SpotSnapshot spotSnapshot
) {
	public ExchangeSnapshot(
					BookTicker futuresTicker,
					FundingRate fundingRate,
					MarkPrice markPrice,
					BookTicker spotTicker
	) {
		this(new FuturesSnapshot(futuresTicker, fundingRate, markPrice), new SpotSnapshot(spotTicker));
	}

	public BookTicker bookTicker(TradeMarket market) {
		if (market == TradeMarket.FUTURES) return futuresSnapshot.bookTicker();
		else return spotSnapshot.bookTicker();
	}

	public Instant fundingSettlement() {
		return futuresSnapshot.fundingRate().settlement();
	}

	public BigDecimal fundingRate() {
		return futuresSnapshot.fundingRate().rate();
	}

	public BigDecimal markPrice() {
		return futuresSnapshot.markPrice().price();
	}
}
