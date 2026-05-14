package com.boris.fundingarbitrage.model.exchange.snapshot;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.strategy.TradeMarket;

public record SpotSnapshot(
				BookTicker bookTicker
) implements Snapshot {
	@Override
	public TradeMarket market() {
		return TradeMarket.SPOT;
	}
}
