package com.boris.fundingarbitrage.model.arbitrage;

import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import lombok.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record ArbitrageSnapshot(
				@NonNull ExchangeSnapshot longExchange, @NonNull ExchangeSnapshot shortExchange
) {
	public Instant closestSettlement() {
		Instant longSettlement = longExchange.fundingRate().settlement();
		Instant shortSettlement = shortExchange.fundingRate().settlement();
		return longSettlement.isBefore(shortSettlement) ? longSettlement : shortSettlement;
	}

	public BigDecimal notional() {
		ExchangeSnapshot longExchange = this.longExchange();
		ExchangeSnapshot shortExchange = this.shortExchange();
		BigDecimal longAsk = longExchange.bookTicker().askPrice();
		BigDecimal shortBid = shortExchange.bookTicker().bidPrice();

		return longAsk.add(shortBid).divide(BigDecimal.TWO, 8, RoundingMode.HALF_EVEN);
	}
}
