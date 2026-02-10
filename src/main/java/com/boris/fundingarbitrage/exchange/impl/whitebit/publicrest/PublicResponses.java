package com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class PublicResponses {
	private static double parseRequiredDouble(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) throw new IllegalStateException("Missing field: " + field);
		String text = val.asText();
		if (text == null || text.isEmpty()) throw new IllegalStateException("Missing field: " + field);
		return Double.parseDouble(text);
	}

	private static long parseRequiredLong(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) throw new IllegalStateException("Missing field: " + field);
		String text = val.asText();
		if (text == null || text.isEmpty()) throw new IllegalStateException("Missing field: " + field);
		return Long.parseLong(text);
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record MarketsResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public MarketsResponse {}

		private JsonNode findMarket(String symbol) {
			if (node == null || !node.isArray()) return null;
			for (JsonNode entry : node) {
				String name = entry.path("name").asText();
				if (!symbol.equalsIgnoreCase(name)) continue;
				String type = entry.path("type").asText();
				if ("futures".equalsIgnoreCase(type)) return entry;
			}
			return null;
		}

		public double lotSizeSymbol(String symbol) {
			JsonNode market = findMarket(symbol);
			if (market == null) throw new IllegalStateException("Market not found: " + symbol);
			return parseRequiredDouble(market, "minAmount");
		}

		public boolean symbolExists(String symbol) {
			return findMarket(symbol) != null;
		}
	}

	public record OrderBookResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public OrderBookResponse {}

		private double parseRequiredLevel(JsonNode level, int idx) {
			if (level == null || !level.isArray() || level.size() <= idx) {
				throw new IllegalStateException("Invalid order book level");
			}
			JsonNode val = level.get(idx);
			if (val == null || val.isNull()) throw new IllegalStateException("Invalid order book level");
			String text = val.asText();
			if (text == null || text.isEmpty()) throw new IllegalStateException("Invalid order book level");
			return Double.parseDouble(text);
		}

		private JsonNode firstLevel(JsonNode levels) {
			if (levels == null || !levels.isArray() || levels.isEmpty()) {
				throw new IllegalStateException("Order book levels missing");
			}
			return levels.get(0);
		}

		public BookTicker bookTicker() {
			if (node == null || !node.isObject()) {
				throw new IllegalStateException("Invalid order book response");
			}
			JsonNode bids = node.get("bids");
			JsonNode asks = node.get("asks");
			JsonNode bestBid = firstLevel(bids);
			JsonNode bestAsk = firstLevel(asks);

			double bidPrice = parseRequiredLevel(bestBid, 0);
			double bidSize = parseRequiredLevel(bestBid, 1);
			double askPrice = parseRequiredLevel(bestAsk, 0);
			double askSize = parseRequiredLevel(bestAsk, 1);

			long ts = node.path("timestamp").asLong();
			if (ts == 0L) throw new IllegalStateException("Missing timestamp in order book");
			Instant timestamp = Instant.ofEpochSecond(ts);
			return new BookTicker(bidPrice, bidSize, askPrice, askSize, timestamp);
		}
	}

	public record FuturesResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public FuturesResponse {}

		private JsonNode findEntry(String symbol) {
			if (node == null || !node.isObject()) return null;
			JsonNode success = node.get("success");
			if (success == null || !success.isBoolean()) {
				throw new IllegalStateException("Missing success flag in futures response");
			}
			if (!success.asBoolean()) {
				throw new IllegalStateException("Futures response indicates failure");
			}
			JsonNode result = node.get("result");
			if (result == null || !result.isArray()) return null;
			for (JsonNode entry : result) {
				String tickerId = entry.path("ticker_id").asText();
				if (symbol.equalsIgnoreCase(tickerId)) return entry;
			}
			return null;
		}

		public double fundingRate(String symbol) {
			JsonNode entry = findEntry(symbol);
			if (entry == null) throw new IllegalStateException("Futures market not found: " + symbol);
			return parseRequiredDouble(entry, "funding_rate");
		}

		public Instant nextFundingTime(String symbol) {
			JsonNode entry = findEntry(symbol);
			if (entry == null) throw new IllegalStateException("Futures market not found: " + symbol);
			long ts = parseRequiredLong(entry, "next_funding_rate_timestamp");
			if (ts < 1_000_000_000_000L) {
				return Instant.ofEpochSecond(ts);
			}
			return Instant.ofEpochMilli(ts);
		}

		public double volume24hMoney(String symbol) {
			JsonNode entry = findEntry(symbol);
			if (entry == null) throw new IllegalStateException("Futures market not found: " + symbol);
			return parseRequiredDouble(entry, "money_volume");
		}

		public int maxLeverage(String symbol) {
			JsonNode entry = findEntry(symbol);
			if (entry == null) throw new IllegalStateException("Futures market not found: " + symbol);
			String text = entry.path("max_leverage").asText();
			if (text == null || text.isEmpty()) throw new IllegalStateException("Missing max_leverage");
			return (int) Math.round(Double.parseDouble(text));
		}

		public double indexPrice(String symbol) {
			JsonNode entry = findEntry(symbol);
			if (entry == null) throw new IllegalStateException("Futures market not found: " + symbol);
			return parseRequiredDouble(entry, "index_price");
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record RecentTradesResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public RecentTradesResponse {}

		private long toMillis(long ts) {
			if (ts < 1_000_000_000_000L) {
				return ts * 1000L;
			}
			return ts;
		}

		public double volume1hQuote() {
			if (node == null || !node.isArray()) throw new IllegalStateException("Trades response missing");
			long cutoff = System.currentTimeMillis() - 60L * 60L * 1000L;
			double total = 0.0;
			boolean any = false;
			for (JsonNode trade : node) {
				long ts = trade.path("trade_timestamp").asLong();
				if (ts == 0L) throw new IllegalStateException("Missing trade_timestamp");
				long tsMillis = toMillis(ts);
				if (tsMillis < cutoff) continue;
				String quoteVolume = trade.path("quote_volume").asText();
				if (quoteVolume == null || quoteVolume.isEmpty()) {
					throw new IllegalStateException("Missing quote_volume");
				}
				any = true;
				total += Double.parseDouble(quoteVolume);
			}
			if (!any) throw new IllegalStateException("No trades in the last hour");
			return total;
		}
	}
}
