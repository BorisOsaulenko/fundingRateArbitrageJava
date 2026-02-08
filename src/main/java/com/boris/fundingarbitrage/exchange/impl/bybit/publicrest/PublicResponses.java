package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class PublicResponses {
	public record InstrumentsInfoResponse(int retCode, String retMsg, long time, JsonNode result) {
		public boolean symbolExists(String symbol) {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return false;
			for (JsonNode item : list) {
				if (symbol.equalsIgnoreCase(item.path("symbol").asText())) return true;
			}
			return false;
		}

		public Double lotSizeSymbol(String symbol) {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return null;
			for (JsonNode item : list) {
				if (!symbol.equalsIgnoreCase(item.path("symbol").asText())) continue;
				JsonNode lotSizeFilter = item.get("lotSizeFilter");
				if (lotSizeFilter == null) return null;
				String qtyStep = lotSizeFilter.path("qtyStep").asText();
				if (qtyStep == null || qtyStep.isEmpty()) return null;
				return Double.parseDouble(qtyStep);
			}
			return null;
		}
	}

	public record TickersResponse(int retCode, String retMsg, long time, JsonNode result) {
		private JsonNode first() {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray() || list.isEmpty()) return null;
			return list.get(0);
		}

		public BookTicker bookTicker() {
			JsonNode item = first();
			if (item == null) return null;
			double bidPrice = item.path("bid1Price").asDouble();
			double bidSize = item.path("bid1Size").asDouble();
			double askPrice = item.path("ask1Price").asDouble();
			double askSize = item.path("ask1Size").asDouble();
			if (bidPrice == 0.0 && bidSize == 0.0 && askPrice == 0.0 && askSize == 0.0) {
				throw new IllegalStateException("Bybit ticker bid/ask price and size missing");
			}

			return new BookTicker(bidPrice, bidSize, askPrice, askSize, Instant.ofEpochMilli(time));
		}

		public FundingRate fundingRate() {
			JsonNode item = first();
			if (item == null) return null;
			double rate = item.path("fundingRate").asDouble();
			long nextFunding = item.path("nextFundingTime").asLong();
			if (rate == 0.0 || nextFunding == 0) {
				throw new IllegalStateException("Bybit funding rate or next funding time missing");
			}

			return new FundingRate(rate, Instant.ofEpochMilli(nextFunding), Instant.ofEpochMilli(time));
		}

		public double volume24h() {
			JsonNode item = first();
			if (item == null) throw new IllegalStateException("Bybit ticker list missing");
			double volume = item.path("volume24h").asDouble();
			if (volume == 0.0) throw new IllegalStateException("Bybit ticker volume24h missing");
			return volume;
		}
	}

	public record KlineResponse(int retCode, String retMsg, long time, JsonNode result) {
		public double volume1h() {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray() || list.isEmpty()) return 0.0;
			JsonNode candle = list.get(0);
			if (!candle.isArray() || candle.size() < 6) return 0.0;

			double volume = candle.get(5).asDouble();
			if (volume == 0.0) throw new IllegalStateException("Bybit kline volume missing");

			return volume;
		}
	}
}
