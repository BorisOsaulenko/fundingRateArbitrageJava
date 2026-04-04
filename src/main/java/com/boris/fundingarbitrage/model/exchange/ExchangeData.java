package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.strategy.TradeMarket;

public record ExchangeData(
				FuturesConstantData futuresConstantData,
				FuturesSnapshot futuresSnapshot,
				SpotConstantData spotConstantData,
				SpotSnapshot spotSnapshot
) {
	public ExchangeData(ExchangeSnapshot sn, ExchangeConstantData cd) {
		this(cd.futuresConstantData(), sn.futuresSnapshot(), cd.spotConstantData(), sn.spotSnapshot());
	}

	public ExchangeConstantData constantData() {
		return new ExchangeConstantData(futuresConstantData(), spotConstantData());
	}

	public ExchangeSnapshot snapshot() {
		return new ExchangeSnapshot(futuresSnapshot(), spotSnapshot());
	}

	public Fees fees(TradeMarket market) {
		if (market == TradeMarket.FUTURES) return futuresConstantData.fees();
		return spotConstantData.fees();
	}

	public BookTicker bookTicker(TradeMarket market) {
		if (market == TradeMarket.FUTURES) return futuresSnapshot.bookTicker();
		return spotSnapshot.bookTicker();
	}
}