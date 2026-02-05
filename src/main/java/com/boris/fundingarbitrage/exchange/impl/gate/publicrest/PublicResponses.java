package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.PriceLevel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class PublicResponses {
	public record ContractResponse(
					String name,
					String order_size_min,
					String leverage_max,
					String maker_fee_rate,
					String taker_fee_rate,
					String funding_rate,
					long funding_next_apply
	) {
		public Double lotSize() {
			if (order_size_min == null || order_size_min.isEmpty()) return null;
			return Double.parseDouble(order_size_min);
		}

		public Integer maxLeverage() {
			if (leverage_max == null || leverage_max.isEmpty()) return null;
			return (int) Math.round(Double.parseDouble(leverage_max));
		}

		public FundingRate fundingRate() {
			if (funding_rate == null || funding_rate.isEmpty()) return null;
			long now = Instant.now().getEpochSecond();
			Instant next = Instant.ofEpochSecond(funding_next_apply);
			return new FundingRate(Double.parseDouble(funding_rate), next, Instant.ofEpochSecond(now));
		}

		public double makerFeeRate() {
			if (maker_fee_rate == null || maker_fee_rate.isEmpty()) return 0.0;
			return Double.parseDouble(maker_fee_rate);
		}

		public double takerFeeRate() {
			if (taker_fee_rate == null || taker_fee_rate.isEmpty()) return 0.0;
			return Double.parseDouble(taker_fee_rate);
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record TickersResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public TickersResponse {}

		private JsonNode first() {
			if (node == null) return null;
			if (node.isArray() && !node.isEmpty()) return node.get(0);
			if (node.isObject()) return node;
			return null;
		}

		public double volume24h() {
			JsonNode item = first();
			if (item == null) throw new IllegalStateException("Invalid ticker response");
			return Double.parseDouble(item.get("volume_24h_quote").asText());
		}
	}

	public record OrderBookResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public OrderBookResponse {}

		private PriceLevel parseBest(JsonNode levels) {
			if (levels == null || !levels.isArray() || levels.isEmpty()) {
				throw new IllegalStateException("Invalid price level response");
			}
			JsonNode best = levels.get(0);
			if (best == null) return null;

			String price = best.path("p").asText();
			String size = best.path("s").asText();

			if (price == null || price.isEmpty()) {
				throw new IllegalStateException("Invalid price response");
			}
			if (size == null || size.isEmpty()) throw new IllegalStateException("Invalid size response");
			return new PriceLevel(Double.parseDouble(price), Double.parseDouble(size));
		}

		public BookTicker bookTicker() {
			if (node == null || !node.isObject()) return null;
			PriceLevel bid = parseBest(node.get("bids"));
			PriceLevel ask = parseBest(node.get("asks"));
			if (bid == null || ask == null) {
				throw new IllegalStateException("Invalid order book response");
			}
			long t = node.path("current").asLong();
			Instant ts = Instant.ofEpochSecond(t);
			return new BookTicker(bid, ask, ts);
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record CandlesticksResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public CandlesticksResponse {}

		public double volume1h() {
			if (node == null || !node.isArray() || node.isEmpty()) {
				throw new IllegalStateException("Invalid candlestick response");
			}
			JsonNode first = node.get(0);
			if (first == null) return 0.0;

			String volume = first.path("sum").asText();
			if (volume == null || volume.isEmpty()) {
				throw new IllegalStateException("Invalid volume response");
			}
			return Double.parseDouble(volume);
		}
	}
}
