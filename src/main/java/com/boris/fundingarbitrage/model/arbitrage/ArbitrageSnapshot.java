package com.boris.fundingarbitrage.model.arbitrage;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import lombok.NonNull;

public record ArbitrageSnapshot(
				@NonNull ExchangeSnapshot longExchange, @NonNull ExchangeSnapshot shortExchange
) {
	@Override
	public @NonNull ExchangeSnapshot longExchange() {
		return longExchange;
	}

	@Override
	public @NonNull ExchangeSnapshot shortExchange() {
		return shortExchange;
	}
}
