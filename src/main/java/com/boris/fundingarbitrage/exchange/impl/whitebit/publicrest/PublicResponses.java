package com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	private record Market(String name, String type, String minAmount) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record MarketsResponse(List<Market> markets) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public MarketsResponse {}

		public Map<String, Double> getLotSizes() {
			Map<String, Double> result = new HashMap<>();
			for (Market market : markets) {
				if (!"futures".equalsIgnoreCase(market.type())) continue;
				result.put(market.name(), Double.parseDouble(market.minAmount()));
			}
			return result;
		}
	}

	public record OrderBookResponse(List<List<String>> bids, List<List<String>> asks, long timestamp) {
		public BookTicker bookTicker() {
			List<String> bestBid = bids.get(0);
			List<String> bestAsk = asks.get(0);
			return new BookTicker(
							Double.parseDouble(bestBid.get(0)),
							Double.parseDouble(bestBid.get(1)),
							Double.parseDouble(bestAsk.get(0)),
							Double.parseDouble(bestAsk.get(1)),
							Instant.ofEpochSecond(timestamp)
			);
		}
	}

	private record FuturesEntry(
					String ticker_id,
					String money_volume,
					String funding_rate,
					double bid,
					double ask,
					long next_funding_rate_timestamp,
					int funding_interval_minutes
	) {}

	public record FuturesResponse(boolean success, String message, List<FuturesEntry> result) {
		private void requireSuccess() {
			if (!success) throw new IllegalStateException("Whitebit futures response failed: " + message);
		}

		public Map<String, Double> getVolume24h() {
			requireSuccess();
			Map<String, Double> resultMap = new HashMap<>();
			for (FuturesEntry entry : result) {
				resultMap.put(entry.ticker_id(), Double.parseDouble(entry.money_volume()));
			}
			return resultMap;
		}

		public Map<String, FundingRate> getFundingRates() {
			requireSuccess();
			Map<String, FundingRate> resultMap = new HashMap<>();
			Instant now = Instant.now();
			for (FuturesEntry entry : result) {
				resultMap.put(
								entry.ticker_id(), new FundingRate(
												Double.parseDouble(entry.funding_rate()),
												parseFundingTimestamp(entry.next_funding_rate_timestamp()),
												now
								)
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
				BookTicker ticker = new BookTicker(entry.bid(), 10, entry.ask(), 10, Instant.now());
				resultMap.put(entry.ticker_id(), ticker);
			}
			return resultMap;
		}
	}

	private static Instant parseFundingTimestamp(long ts) {
		if (ts < 1_000_000_000_000L) return Instant.ofEpochSecond(ts);
		return Instant.ofEpochMilli(ts);
	}
}
