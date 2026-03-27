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
		Instant longSettlement = longExchange.futuresSnapshot().fundingRate().settlement();
		Instant shortSettlement = shortExchange.futuresSnapshot().fundingRate().settlement();
		return longSettlement.isBefore(shortSettlement) ? longSettlement : shortSettlement;
	}

	public BigDecimal futuresNotional() {
		BigDecimal longAsk = longExchange().futuresSnapshot().bookTicker().askPrice();
		BigDecimal shortBid = shortExchange().futuresSnapshot().bookTicker().bidPrice();

		return longAsk.add(shortBid).divide(BigDecimal.TWO, 8, RoundingMode.HALF_UP);
	}

	public BigDecimal spotNotional() {
		BigDecimal longBid = longExchange().spotSnapshot().bookTicker().bidPrice();
		BigDecimal shortAsk = shortExchange().spotSnapshot().bookTicker().askPrice();

		return longBid.add(shortAsk).divide(BigDecimal.TWO, 8, RoundingMode.HALF_UP);
	}
}
