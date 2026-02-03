package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.PriceLevel;

import java.time.Instant;

public class PublicResponses {
	public record Contract(
					String symbol,
					String sizeMultiplier,
					String minTradeNum,
					String makerFeeRate,
					String takerFeeRate,
					Integer maxLever
	) {}

	public record ContractsResponse(String code, String msg, long requestTime, Contract[] data) {
		public boolean symbolExists(String symbol) {
			if (data == null) return false;
			for (Contract contract : data) {
				if (symbol.equalsIgnoreCase(contract.symbol())) return true;
			}
			return false;
		}

		public Double lotSize(String symbol) {
			if (data == null) return null;
			for (Contract contract : data) {
				if (symbol.equalsIgnoreCase(contract.symbol())) {
					String size = contract.sizeMultiplier();
					if (size == null || size.isEmpty()) size = contract.minTradeNum();
					return size == null ? null : Double.parseDouble(size);
				}
			}
			return null;
		}

		public Integer maxLeverage(String symbol) {
			if (data == null) return null;
			for (Contract contract : data) {
				if (symbol.equalsIgnoreCase(contract.symbol())) {
					return contract.maxLever();
				}
			}
			return null;
		}

		public double makerFeeRate(String symbol) {
			if (data == null) return 0.0;
			for (Contract contract : data) {
				if (symbol.equalsIgnoreCase(contract.symbol())) {
					return contract.makerFeeRate() == null ? 0.0 : Double.parseDouble(contract.makerFeeRate());
				}
			}
			return 0.0;
		}

		public double takerFeeRate(String symbol) {
			if (data == null) return 0.0;
			for (Contract contract : data) {
				if (symbol.equalsIgnoreCase(contract.symbol())) {
					return contract.takerFeeRate() == null ? 0.0 : Double.parseDouble(contract.takerFeeRate());
				}
			}
			return 0.0;
		}
	}

	public record Ticker(
					String symbol,
					String bidPr,
					String askPr,
					String bidSz,
					String askSz,
					String markPrice,
					String fundingRate,
					String nextFundingTime,
					String baseVolume,
					String ts
	) {}

	public record TickerResponse(String code, String msg, long requestTime, Ticker[] data) {
		public BookTicker bookTicker() {
			if (data.length == 0) return null;
			Ticker ticker = data[0];
			PriceLevel bid = new PriceLevel(
							Double.parseDouble(ticker.bidPr()),
							Double.parseDouble(ticker.bidSz())
			);
			PriceLevel ask = new PriceLevel(
							Double.parseDouble(ticker.askPr()),
							Double.parseDouble(ticker.askSz())
			);
			Instant timestamp = toInstant(ticker.ts(), requestTime);
			return new BookTicker(bid, ask, timestamp);
		}

		public double volume24h() {
			if (data.length == 0) return 0.0;
			return Double.parseDouble(data[0].baseVolume());
		}

		private Instant toInstant(String ts, long fallback) {
			if (ts == null || ts.isEmpty()) return Instant.ofEpochMilli(fallback);
			return Instant.ofEpochMilli(Long.parseLong(ts));
		}
	}

	public record FundingRateEntry(String symbol, String fundingRate, String nextUpdate) {}

	public record FundingRateResponse(
					String code, String msg, long requestTime, FundingRateEntry[] data
	) {
		public FundingRate get() {
			if (data.length == 0 || data[0] == null) return null;
			double rate = Double.parseDouble(data[0].fundingRate);
			Instant settlement = Instant.ofEpochMilli(Long.parseLong(data[0].nextUpdate()));
			Instant timestamp = Instant.ofEpochMilli(requestTime);
			return new FundingRate(rate, settlement, timestamp);
		}
	}

	public record CandlesResponse(String code, String msg, long requestTime, String[][] data) {
		public double volume1h() {
			if (data == null || data.length == 0) return 0.0;
			String[] candle = data[0];
			if (candle.length < 6) return 0.0;
			return Double.parseDouble(candle[5]);
		}
	}
}
