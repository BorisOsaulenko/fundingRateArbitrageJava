package com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest;

import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	private static Instant parseFundingTimestamp(long ts) {
		if (ts < 1_000_000_000_000L) return Instant.ofEpochSecond(ts);
		return Instant.ofEpochMilli(ts);
	}

	private record Market(String name, String type, BigDecimal minAmount, boolean tradesEnabled) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record MarketsResponse(List<Market> markets) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public MarketsResponse {
		}

		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (Market market : markets) {
				if (!"futures".equalsIgnoreCase(market.type())) continue;
				result.put(market.name(), market.minAmount());
			}
			return result;
		}

		public Map<String, FuturesTradingState> getTradingStates() {
			Map<String, FuturesTradingState> result = new HashMap<>();
			for (Market market : markets) {
				if (!market.tradesEnabled()) result.put(market.name(), FuturesTradingState.NOT_TRADING);
				else result.put(market.name(), FuturesTradingState.TRADING);
			}
			return result;
		}
	}

	private record FuturesEntry(
					String ticker_id,
					BigDecimal money_volume,
					BigDecimal funding_rate,
					BigDecimal bid,
					BigDecimal ask,
					long next_funding_rate_timestamp,
					int funding_interval_minutes
	) {
	}

	public record FuturesResponse(boolean success, String message, List<FuturesEntry> result) {
		private void requireSuccess() {
			if (!success) throw new IllegalStateException("Whitebit futures response failed: " + message);
		}

		public Map<String, BigDecimal> getVolume24h() {
			requireSuccess();
			Map<String, BigDecimal> resultMap = new HashMap<>();
			for (FuturesEntry entry : result) {
				resultMap.put(entry.ticker_id(), entry.money_volume());
			}
			return resultMap;
		}

		public Map<String, FundingRate> getFundingRates() {
			requireSuccess();
			Map<String, FundingRate> resultMap = new HashMap<>();
			Instant now = Instant.now();
			for (FuturesEntry entry : result) {
				resultMap.put(
								entry.ticker_id(),
								new FundingRate(entry.funding_rate(), parseFundingTimestamp(entry.next_funding_rate_timestamp()), now)
				);
			}
			return resultMap;
		}

		public Map<String, Integer> getFundingGranularityHours() {
			requireSuccess();
			Map<String, Integer> resultMap = new HashMap<>();
			for (FuturesEntry entry : result) {
				resultMap.put(entry.ticker_id(), entry.funding_interval_minutes() / 60);
			}
			return resultMap;
		}

		public Map<String, BookTicker> getBookTickers() {
			requireSuccess();
			Map<String, BookTicker> resultMap = new HashMap<>();
			for (FuturesEntry entry : result) {
				// We use 10 because Whitebit does not provide the bid/ask sizes. Does not affect logic.
				BookTicker ticker = new BookTicker(entry.bid(), BigDecimal.TEN, entry.ask(), BigDecimal.TEN, Instant.now());
				resultMap.put(entry.ticker_id(), ticker);
			}
			return resultMap;
		}
	}
}
