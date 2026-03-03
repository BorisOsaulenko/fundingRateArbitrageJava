package com.boris.fundingarbitrage.model.arbitrage;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import lombok.NonNull;

import java.time.Instant;

public record ArbitrageSnapshot(
				@NonNull ExchangeSnapshot longExchange, @NonNull ExchangeSnapshot shortExchange
) {
	public Instant closestSettlement() {
		Instant longSettlement = longExchange.fundingRate().settlement();
		Instant shortSettlement = shortExchange.fundingRate().settlement();
		return longSettlement.isBefore(shortSettlement) ? longSettlement : shortSettlement;
	}
}
