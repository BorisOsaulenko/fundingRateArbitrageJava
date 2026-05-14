package com.boris.fundingarbitrage.model.exchange.snapshot;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.strategy.TradeMarket;

public record FuturesSnapshot(
				BookTicker bookTicker,
				Funding funding,
				Mark mark
) implements Snapshot {
	@Override
	public TradeMarket market() {
		return TradeMarket.FUTURES;
	}
}