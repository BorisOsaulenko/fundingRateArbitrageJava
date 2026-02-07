package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinJson;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.PriceLevel;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class PublicResponses {
	public record ContractResponse(String code, String msg, JsonNode data) {
		private JsonNode requireData() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			return data;
		}

		public boolean symbolMatches(String symbol) {
			JsonNode node = requireData();
			String actual = KucoinJson.requireText(node, "symbol");
			return symbol.equalsIgnoreCase(actual);
		}

		public double lotSize() {
			JsonNode node = requireData();
			return KucoinJson.requireDouble(node, "lotSize");
		}

		public double volume24h() {
			JsonNode node = requireData();
			return KucoinJson.requireDouble(node, "volumeOf24h");
		}
	}

	public record TickerResponse(String code, String msg, JsonNode data) {
		public BookTicker bookTicker() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin ticker data missing");
			}
			double bidPrice = KucoinJson.requireDouble(data, "bestBidPrice");
			double bidSize = KucoinJson.requireDouble(data, "bestBidSize");
			double askPrice = KucoinJson.requireDouble(data, "bestAskPrice");
			double askSize = KucoinJson.requireDouble(data, "bestAskSize");
			long ts = KucoinJson.requireLong(data, "ts");
			Instant timestamp = KucoinJson.toInstantMillisOrNanos(ts);
			return new BookTicker(
					new PriceLevel(bidPrice, bidSize),
					new PriceLevel(askPrice, askSize),
					timestamp
			);
		}
	}

	public record FundingRateResponse(String code, String msg, JsonNode data) {
		public FundingRate fundingRate() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin funding rate data missing");
			}
			double rate = KucoinJson.requireDouble(data, "value");
			long timePoint = KucoinJson.requireLong(data, "timePoint");
			long fundingTime = KucoinJson.requireLong(data, "fundingTime");
			return new FundingRate(
					rate,
					Instant.ofEpochMilli(fundingTime),
					Instant.ofEpochMilli(timePoint)
			);
		}
	}

	public record KlinesResponse(String code, String msg, JsonNode data) {
		public double volume1h() {
			KucoinJson.requireCodeOk(code, msg);
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
