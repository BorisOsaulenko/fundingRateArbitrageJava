package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PublicResponses {
	private static final String expectedSuccessCode = "200000";

	public record ContractResponse(String code, String msg, JsonNode data) {
		private JsonNode requireData() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			return data;
		}

		public boolean symbolMatches(String symbol) {
			JsonNode node = requireData();
			String actual = node.path("symbol").asText();
			return symbol.equalsIgnoreCase(actual);
		}

		public double lotSize() {
			JsonNode node = requireData();
			double size = node.path("lotSize").asDouble();
			if (size == 0.0) throw new IllegalStateException("KuCoin contract lotSize missing");

			return size;
		}

		public double volume24h() {
			JsonNode node = requireData();
			double volume = node.path("volumeOf24h").asDouble();
			if (volume == 0.0) throw new IllegalStateException("KuCoin contract volumeOf24h missing");

			return volume;
		}
	}

	public record TickerResponse(String code, String msg, JsonNode data) {
		public BookTicker bookTicker() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin ticker data missing");
			}
			double bidPrice = data.path("bestBidPrice").asDouble();
			if (bidPrice == 0.0) throw new IllegalStateException("KuCoin ticker bestBidPrice missing");

			double bidSize = data.path("bestBidSize").asDouble();
			if (bidSize == 0.0) throw new IllegalStateException("KuCoin ticker bestBidSize missing");

			double askPrice = data.path("bestAskPrice").asDouble();
			if (askPrice == 0.0) throw new IllegalStateException("KuCoin ticker bestAskPrice missing");

			double askSize = data.path("bestAskSize").asDouble();
			if (askSize == 0.0) throw new IllegalStateException("KuCoin ticker bestAskSize missing");

			long ts = data.path("ts").asLong();
			if (ts == 0L) throw new IllegalStateException("KuCoin ticker ts missing");

			Instant timestamp = Instant.ofEpochMilli(ts / 1_000_000); // KuCoin ts is in nanoseconds, convert to milliseconds
			return new BookTicker(bidPrice, bidSize, askPrice, askSize, timestamp);
		}
	}

	public record FundingRateResponse(String code, String msg, JsonNode data) {
		public FundingRate fundingRate() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin funding rate data missing");
			}

			double rate = data.path("value").asDouble();
			if (rate == 0.0) throw new IllegalStateException("KuCoin funding rate value missing");

			long timePoint = data.path("timePoint").asLong();
			if (timePoint == 0L) throw new IllegalStateException("KuCoin funding rate timePoint missing");

			long fundingTime = data.path("fundingTime").asLong();
			if (fundingTime == 0L) throw new IllegalStateException("KuCoin funding rate fundingTime missing");

			return new FundingRate(rate, Instant.ofEpochMilli(fundingTime), Instant.ofEpochMilli(timePoint));
		}
	}

	public record KlinesResponse(String code, String msg, JsonNode data) {
		public double volume1h() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("KuCoin kline data missing");
			}
			JsonNode candle = data.get(0);
			if (!candle.isArray() || candle.size() <= 5) {
				throw new IllegalStateException("KuCoin kline candle missing volume");
			}
			String volumeText = candle.get(5).asText();
			if (volumeText == null || volumeText.isEmpty()) {
				throw new IllegalStateException("KuCoin kline volume missing");
			}
			try {
				return Double.parseDouble(volumeText);
			} catch (NumberFormatException ex) {
				throw new IllegalStateException("Invalid KuCoin kline volume", ex);
			}
		}
	}

	public record ActiveContractsResponse(String code, String msg, JsonNode data) {
		private JsonNode getDataArray() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin active contracts response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("KuCoin active contracts data missing");
			}
			return data;
		}

		public Map<String, Boolean> existsBySymbols(List<String> symbols) {
			Map<String, Boolean> existsBySymbol = new HashMap<>();
			for (String symbol : symbols) {
				existsBySymbol.put(symbol, false);
			}
			for (JsonNode contract : getDataArray()) {
				String symbol = contract.path("symbol").asText();
				if (!existsBySymbol.containsKey(symbol)) continue;
				String status = contract.path("status").asText();
				existsBySymbol.put(symbol, "open".equalsIgnoreCase(status));
			}
			return existsBySymbol;
		}

		public Map<String, FundingRate> fundingRatesBySymbols(List<String> symbols) {
			Map<String, FundingRate> fundingBySymbol = new HashMap<>();
			for (JsonNode contract : getDataArray()) {
				String symbol = contract.path("symbol").asText();
				if (!symbols.contains(symbol)) continue;

				double rate = contract.path("fundingFeeRate").asDouble();
				long settlementMs = contract.path("nextFundingRateDateTime").asLong();
				if (rate == 0.0 || settlementMs == 0L) continue;

				fundingBySymbol.put(symbol, new FundingRate(rate, Instant.ofEpochMilli(settlementMs), Instant.now()));
			}
			return fundingBySymbol;
		}
	}
}
