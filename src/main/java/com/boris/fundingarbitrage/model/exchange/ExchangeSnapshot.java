package com.boris.fundingarbitrage.model.exchange;

public record ExchangeSnapshot(
				FuturesSnapshot futuresSnapshot,
				SpotSnapshot spotSnapshot
) {
}
