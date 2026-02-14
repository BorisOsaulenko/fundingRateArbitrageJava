package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	private record Contract(
					String symbol,
					String sizeMultiplier,
					String minTradeNum,
					String makerFeeRate,
					String takerFeeRate,
					Integer maxLever
	) {}

	public record ContractsResponse(String code, String msg, long requestTime, Contract[] data) {
		public boolean existsSymbol(String symbol) {
			if (data == null) return false;
			for (Contract contract : data) {
				if (symbol.equalsIgnoreCase(contract.symbol())) return true;
			}
			return false;
		}

		public Double lotSizeSymbol(String symbol) {
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
	}

	public record ContractsSymbolsResponse(String code, String msg, long requestTime, Contract[] data) {
		public Map<String, Boolean> existsBySymbols(List<String> symbols) {
			Map<String, Boolean> result = new HashMap<>();
			for (String symbol : symbols) {
				result.put(symbol, false);
			}
			if (data == null) return result;
			for (Contract contract : data) {
				if (result.containsKey(contract.symbol())) {
					result.put(contract.symbol(), true);
				}
			}
			return result;
		}
	}

	private record Ticker(
					String symbol,
					String bidPr,
					String askPr,
					String bidSz,
					String askSz,
					String markPrice,
					String fundingRate,
					String baseVolume,
					String ts
	) {}

	public record TickerResponse(String code, String msg, long requestTime, Ticker[] data) {
		public BookTicker bookTicker() {
			if (data.length == 0) return null;
			Ticker ticker = data[0];
			Instant timestamp = Instant.ofEpochMilli(requestTime);
			return new BookTicker(
							Double.parseDouble(ticker.bidPr()),
							Double.parseDouble(ticker.bidSz()),
							Double.parseDouble(ticker.askPr()),
							Double.parseDouble(ticker.askSz()),
							timestamp
			);
		}

		public double volume24h() {
			if (data.length == 0) return 0.0;
			return Double.parseDouble(data[0].baseVolume());
		}
	}

	private record FundingRateEntrySymbol(
					String symbol, String fundingRate, String nextUpdate
	) {}

	public record FundingRatesResponseSymbols(String code, String msg, long requestTime, FundingRateEntrySymbol[] data) {
		public Map<String, FundingRate> get(List<String> symbols) {
			Map<String, FundingRate> result = new HashMap<>();
			if (data == null) return result;
			for (var ticker : data) {
				if (!symbols.contains(ticker.symbol())) continue;
				double rate = Double.parseDouble(ticker.fundingRate());
				Instant settlement = Instant.ofEpochMilli(Long.parseLong(ticker.nextUpdate()));
				Instant timestamp = Instant.ofEpochMilli(requestTime);
				result.put(ticker.symbol(), new FundingRate(rate, settlement, timestamp));
			}
			return result;
		}
	}

	private record FundingRateEntry(String symbol, String fundingRate, String nextUpdate) {}

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

	private record FundingGranularityEntry(String symbol, String fundingRateInterval) {}

	public record FundingGranularityResponse(String code, String msg, long requestTime, FundingGranularityEntry[] data) {
		public Map<String, Integer> get(List<String> symbols) {
			Map<String, Integer> result = new HashMap<>();
			if (data == null) return result;
			for (var ticker : data) {
				if (!symbols.contains(ticker.symbol())) continue;
				result.put(ticker.symbol(), Integer.parseInt(ticker.fundingRateInterval()));
			}
			return result;
		}
	}
}
