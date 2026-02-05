package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.PriceLevel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class PublicResponses {
	private static Instant parseEpoch(String value, long fallbackSeconds) {
		if (value == null || value.isEmpty()) return Instant.ofEpochSecond(fallbackSeconds);
		try {
			long ts = Long.parseLong(value);
			if (ts > 10_000_000_000L) {
				return Instant.ofEpochMilli(ts);
			}
			return Instant.ofEpochSecond(ts);
		} catch (NumberFormatException ex) {
			return Instant.ofEpochSecond(fallbackSeconds);
		}
	}

	private static double parseDouble(JsonNode node, String field) {
		if (node == null) return 0.0;
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return 0.0;
		String text = val.asText();
		if (text == null || text.isEmpty()) return 0.0;
		return Double.parseDouble(text);
	}

	public record ContractResponse(
				String name,
				String order_size_min,
				String leverage_max,
				String maker_fee_rate,
				String taker_fee_rate,
				String funding_rate,
				String funding_next_apply
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
			Instant next = parseEpoch(funding_next_apply, now);
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
			if (item == null) return 0.0;
			double base = parseDouble(item, "volume_24h_base");
			if (base > 0) return base;
			return parseDouble(item, "volume_24h");
		}
	}

	public record OrderBookResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public OrderBookResponse {}

		private PriceLevel parseBest(JsonNode levels, boolean bid) {
			if (levels == null || !levels.isArray() || levels.isEmpty()) return null;
			JsonNode best = levels.get(0);
			if (best == null) return null;
			String price;
			String size;
			if (best.isArray()) {
				price = best.size() > 0 ? best.get(0).asText() : null;
				size = best.size() > 1 ? best.get(1).asText() : null;
			} else {
				price = best.path("p").asText();
				size = best.path("s").asText();
				if ((price == null || price.isEmpty()) && bid) price = best.path("price").asText();
				if ((size == null || size.isEmpty()) && bid) size = best.path("size").asText();
			}
			if (price == null || price.isEmpty()) return null;
			if (size == null || size.isEmpty()) size = "0";
			return new PriceLevel(Double.parseDouble(price), Double.parseDouble(size));
		}

		public BookTicker bookTicker() {
			if (node == null || !node.isObject()) return null;
			PriceLevel bid = parseBest(node.get("bids"), true);
			PriceLevel ask = parseBest(node.get("asks"), false);
			if (bid == null || ask == null) return null;
			long t = node.path("t").asLong(Instant.now().getEpochSecond());
			Instant ts = t > 10_000_000_000L ? Instant.ofEpochMilli(t) : Instant.ofEpochSecond(t);
			return new BookTicker(bid, ask, ts);
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record CandlesticksResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public CandlesticksResponse {}

		public double volume1h() {
			if (node == null || !node.isArray() || node.isEmpty()) return 0.0;
			JsonNode first = node.get(0);
			if (first == null) return 0.0;
			if (first.isObject()) {
				return parseDouble(first, "v");
			}
			if (first.isArray()) {
				if (first.size() > 1) {
					return Double.parseDouble(first.get(1).asText());
				}
			}
			return 0.0;
		}
	}
}
