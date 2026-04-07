package com.boris.fundingarbitrage.model.exchange.snapshot;

import com.boris.fundingarbitrage.model.contract.BookTicker;

import java.math.BigDecimal;

public sealed interface Snapshot permits FuturesSnapshot, SpotSnapshot {
	BookTicker bookTicker();

	default BigDecimal bidPrice() {
		return bookTicker().bidPrice();
	}

	default BigDecimal askPrice() {
		return bookTicker().askPrice();
	}
}