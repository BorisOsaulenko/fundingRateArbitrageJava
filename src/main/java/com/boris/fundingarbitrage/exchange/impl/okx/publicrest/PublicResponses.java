package com.boris.fundingarbitrage.exchange.impl.okx.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	private static void ensureOk(int code, String msg) {
		if (code != 0) throw new RuntimeException(String.format("OKX public request failed: %s, %s", code, msg));
	}

	public record InstrumentsResponse(int code, String msg, JsonNode data) {
		private JsonNode findSymbol(String symbol) {
			if (data == null || !data.isArray()) return null;
			for (JsonNode item : data) {
				if (symbol.equalsIgnoreCase(item.path("instId").asText())) return item;
			}
			return null;
		}

		public double lotSizeSymbol(String symbol) {
			ensureOk(code, msg);
			JsonNode item = findSymbol(symbol);
			if (item == null) throw new IllegalStateException("OKX instrument not found for symbol: " + symbol);
			String lotSz = item.path("lotSz").asText();
			if (lotSz == null || lotSz.isEmpty()) {
				throw new IllegalStateException("OKX instrument lotSz missing for symbol: " + symbol);
			}
			return Double.parseDouble(lotSz);
		}

		public int maxLeverageSymbol(String symbol) {
			ensureOk(code, msg);
			JsonNode item = findSymbol(symbol);
			if (item == null) throw new IllegalStateException("OKX instrument not found for symbol: " + symbol);
			String lever = item.path("lever").asText();
			if (lever == null || lever.isEmpty()) {
				throw new IllegalStateException("OKX instrument lever missing for symbol: " + symbol);
			}
			return (int) Math.floor(Double.parseDouble(lever));
		}
	}

	public record TickerResponse(int code, String msg, JsonNode data) {
		private JsonNode first() {
			if (data == null || !data.isArray() || data.isEmpty()) return null;
			return data.get(0);
		}

		public BookTicker bookTicker() {
			ensureOk(code, msg);
			JsonNode item = first();
			if (item == null) throw new IllegalStateException("OKX ticker data missing");

			double bidPrice = Double.parseDouble(item.path("bidPx").asText());
			double bidSize = Double.parseDouble(item.path("bidSz").asText());
			double askPrice = Double.parseDouble(item.path("askPx").asText());
			double askSize = Double.parseDouble(item.path("askSz").asText());
			String tsText = item.path("ts").asText();
			if (tsText == null || tsText.isEmpty()) {
				throw new IllegalStateException("OKX ticker ts missing");
			}
			Instant ts = Instant.ofEpochMilli(Long.parseLong(tsText));

			return new BookTicker(bidPrice, bidSize, askPrice, askSize, ts);
		}

		public double volume24h() {
			ensureOk(code, msg);
			JsonNode item = first();
			if (item == null) throw new IllegalStateException("OKX ticker data missing");
			String vol = item.path("volCcy24h").asText();
			if (vol == null || vol.isEmpty()) {
				throw new IllegalStateException("OKX ticker volCcy24h missing");
			}
			return Double.parseDouble(vol);
		}
	}

	public record FundingRateResponse(int code, String msg, JsonNode data) {
		private JsonNode first() {
			if (data == null || !data.isArray() || data.isEmpty()) return null;
			return data.get(0);
		}

		public FundingRate fundingRate() {
			ensureOk(code, msg);
			JsonNode item = first();
			if (item == null) throw new IllegalStateException("OKX funding rate data missing");
			String rateText = item.path("fundingRate").asText();
			String nextFundingText = item.path("fundingTime").asText();
			String tsText = item.path("ts").asText();
			if (rateText == null || rateText.isEmpty()) {
				throw new IllegalStateException("OKX fundingRate missing");
			}
			if (nextFundingText == null || nextFundingText.isEmpty()) {
				throw new IllegalStateException("OKX nextFundingTime missing");
			}
			if (tsText == null || tsText.isEmpty()) {
				throw new IllegalStateException("OKX funding rate ts missing");
			}
			double rate = Double.parseDouble(rateText);
			Instant nextFunding = Instant.ofEpochMilli(Long.parseLong(nextFundingText));
			Instant ts = Instant.ofEpochMilli(Long.parseLong(tsText));
			return new FundingRate(rate, nextFunding, ts);
		}
	}

	public record FundingRatesSymbolsResponse(int code, String msg, JsonNode data) {
		public Map<String, FundingRate> getBySymbols(List<String> symbols) {
			ensureOk(code, msg);
			Map<String, FundingRate> ratesBySymbol = new HashMap<>();
			if (data == null || !data.isArray()) return ratesBySymbol;

			for (JsonNode item : data) {
				String symbol = item.path("instId").asText();
				if (symbol == null || symbol.isEmpty() || !symbols.contains(symbol)) continue;

				String rateText = item.path("fundingRate").asText();
				String nextFundingText = item.path("fundingTime").asText();
				String tsText = item.path("ts").asText();
				if (rateText == null || rateText.isEmpty()) continue;
				if (nextFundingText == null || nextFundingText.isEmpty()) continue;
				if (tsText == null || tsText.isEmpty()) continue;

				double rate = Double.parseDouble(rateText);
				Instant nextFunding = Instant.ofEpochMilli(Long.parseLong(nextFundingText));
				Instant ts = Instant.ofEpochMilli(Long.parseLong(tsText));
				ratesBySymbol.put(symbol, new FundingRate(rate, nextFunding, ts));
			}
			return ratesBySymbol;
		}
	}

	public record MarkPriceResponse(int code, String msg, JsonNode data) {
		private JsonNode first() {
			if (data == null || !data.isArray() || data.isEmpty()) return null;
			return data.get(0);
		}

		public double markPrice() {
			ensureOk(code, msg);
			JsonNode item = first();
			if (item == null) throw new IllegalStateException("OKX mark price data missing");
			String markPx = item.path("markPx").asText();
			if (markPx == null || markPx.isEmpty()) {
				throw new IllegalStateException("OKX markPx missing");
			}
			return Double.parseDouble(markPx);
		}

		public Instant timestamp() {
			JsonNode item = first();
			if (item == null) throw new IllegalStateException("OKX mark price data missing");
			String tsText = item.path("ts").asText();
			if (tsText == null || tsText.isEmpty()) {
				throw new IllegalStateException("OKX mark price ts missing");
			}
			return Instant.ofEpochMilli(Long.parseLong(tsText));
		}
	}

	public record CandlesResponse(int code, String msg, JsonNode data) {
		public double volume1h() {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX candles data missing");
			}
			JsonNode candle = data.get(0);
			if (!candle.isArray() || candle.size() < 7) {
				throw new IllegalStateException("OKX candle fields missing");
			}
			String volCcy = candle.get(6).asText();
			if (volCcy == null || volCcy.isEmpty()) {
				throw new IllegalStateException("OKX candle volCcy missing");
			}
			return Double.parseDouble(volCcy);
		}
	}

	public record InstrumentsSymbolsResponse(int code, String msg, JsonNode data) {
		public Map<String, Boolean> existsBySymbols(List<String> symbols) {
			ensureOk(code, msg);
			Map<String, Boolean> result = new HashMap<>();
			for (String symbol : symbols) {
				result.put(symbol, false);
			}
			if (data == null || !data.isArray()) return result;
			for (JsonNode item : data) {
				String instId = item.path("instId").asText();
				if (result.containsKey(instId)) result.put(instId, true);
			}
			return result;
		}
	}

	private record FundingGranularityEntry(String instId, long fundingTime, long nextFundingTime) {}

	public record FundingGranularityResponse(int code, String msg, List<FundingGranularityEntry> data) {
		public Map<String, Integer> get(List<String> symbols) {
			Map<String, Integer> result = new HashMap<>();
			for (var entry : data) {
				if (!symbols.contains(entry.instId)) continue;
				result.put(entry.instId, (int) ((entry.nextFundingTime - entry.fundingTime) / 3600000L));
			}

			return result;
		}
	}
}
