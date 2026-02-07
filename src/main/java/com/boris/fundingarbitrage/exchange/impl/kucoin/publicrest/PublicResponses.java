package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.PriceLevel;
import com.boris.fundingarbitrage.util.json.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class PublicResponses {
	private static final String expectedSuccessCode = "200000";

	public record ContractResponse(String code, String msg, JsonNode data) {
		private JsonNode requireData() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			return data;
		}

		public boolean symbolMatches(String symbol) {
			JsonNode node = requireData();
			String actual = Json.requireText(node, "symbol");
			return symbol.equalsIgnoreCase(actual);
		}

		public double lotSize() {
			JsonNode node = requireData();
			return Json.requireDouble(node, "lotSize");
		}

		public double volume24h() {
			JsonNode node = requireData();
			return Json.requireDouble(node, "volumeOf24h");
		}
	}

	public record TickerResponse(String code, String msg, JsonNode data) {
		public BookTicker bookTicker() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin ticker data missing");
			}
			double bidPrice = Json.requireDouble(data, "bestBidPrice");
			double bidSize = Json.requireDouble(data, "bestBidSize");
			double askPrice = Json.requireDouble(data, "bestAskPrice");
			double askSize = Json.requireDouble(data, "bestAskSize");
			long ts = Json.requireLong(data, "ts");
			Instant timestamp = Json.toInstantMillisOrNanos(ts);
			return new BookTicker(new PriceLevel(bidPrice, bidSize), new PriceLevel(askPrice, askSize), timestamp);
		}
	}

	public record FundingRateResponse(String code, String msg, JsonNode data) {
		public FundingRate fundingRate() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin funding rate data missing");
			}
			double rate = Json.requireDouble(data, "value");
			long timePoint = Json.requireLong(data, "timePoint");
			long fundingTime = Json.requireLong(data, "fundingTime");
			return new FundingRate(rate, Instant.ofEpochMilli(fundingTime), Instant.ofEpochMilli(timePoint));
		}
	}

	public record KlinesResponse(String code, String msg, JsonNode data) {
		public double volume1h() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
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
}
