package com.boris.fundingarbitrage.model.exchange.snapshot;

import com.boris.fundingarbitrage.model.contract.BookTicker;

public record SpotSnapshot(
				BookTicker bookTicker
) implements Snapshot {
}
