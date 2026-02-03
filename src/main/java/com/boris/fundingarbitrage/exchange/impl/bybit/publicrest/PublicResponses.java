package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.PriceLevel;
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

		public Double lotSize(String symbol) {
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

		public Integer maxLeverage(String symbol) {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return null;
			for (JsonNode item : list) {
				if (!symbol.equalsIgnoreCase(item.path("symbol").asText())) continue;
				JsonNode leverageFilter = item.get("leverageFilter");
				if (leverageFilter == null) return null;
				String maxLeverage = leverageFilter.path("maxLeverage").asText();
				if (maxLeverage == null || maxLeverage.isEmpty()) return null;
				return (int) Math.round(Double.parseDouble(maxLeverage));
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
			String bidPrice = item.path("bid1Price").asText();
			String bidSize = item.path("bid1Size").asText();
			String askPrice = item.path("ask1Price").asText();
			String askSize = item.path("ask1Size").asText();
			if (bidPrice == null || bidPrice.isEmpty() || askPrice == null || askPrice.isEmpty()) return null;
			PriceLevel bid = new PriceLevel(Double.parseDouble(bidPrice), Double.parseDouble(bidSize));
			PriceLevel ask = new PriceLevel(Double.parseDouble(askPrice), Double.parseDouble(askSize));
			return new BookTicker(bid, ask, Instant.ofEpochMilli(time));
		}

		public FundingRate fundingRate() {
			JsonNode item = first();
			if (item == null) return null;
			String rate = item.path("fundingRate").asText();
			String nextFunding = item.path("nextFundingTime").asText();
			if (rate == null || rate.isEmpty() || nextFunding == null || nextFunding.isEmpty()) return null;
			return new FundingRate(
					Double.parseDouble(rate),
					Instant.ofEpochMilli(Long.parseLong(nextFunding)),
					Instant.ofEpochMilli(time)
			);
		}

		public double volume24h() {
			JsonNode item = first();
			if (item == null) return 0.0;
			String volume = item.path("volume24h").asText();
			if (volume == null || volume.isEmpty()) return 0.0;
			return Double.parseDouble(volume);
		}
	}

	public record KlineResponse(int retCode, String retMsg, long time, JsonNode result) {
		public double volume1h() {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray() || list.isEmpty()) return 0.0;
			JsonNode candle = list.get(0);
			if (!candle.isArray() || candle.size() < 6) return 0.0;
			return Double.parseDouble(candle.get(5).asText());
		}
	}
}
