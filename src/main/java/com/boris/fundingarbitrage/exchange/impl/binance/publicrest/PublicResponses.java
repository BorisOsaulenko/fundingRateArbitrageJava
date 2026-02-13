package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PublicResponses {
	private record Filter(String filterType, String minQty, String maxQty, String stepSize) {}

	private record SymbolInfo(String symbol, Filter[] filters) {}

	public record CheckExistsSymbolResponse(SymbolInfo[] symbols) {
		public boolean get(String symbol) {
			if (symbols == null) return false;

			for (SymbolInfo symbolInfo : symbols) {
				if (symbolInfo.symbol().equals(symbol)) {
					return true;
				}
			}
			return false;
		}
	}

	public record CheckExistsSymbolsResponse(SymbolInfo[] symbols) {
		public Map<String, Boolean> get(List<String> symbolsToCheck) {
			Map<String, Boolean> existsBySymbol = new HashMap<>();
			for (String symbol : symbolsToCheck) {
				existsBySymbol.put(symbol, false);
			}
			if (symbols == null) return existsBySymbol;

			for (SymbolInfo symbolInfo : symbols) {
				if (existsBySymbol.containsKey(symbolInfo.symbol())) {
					existsBySymbol.put(symbolInfo.symbol(), true);
				}
			}
			return existsBySymbol;
		}
	}

	public record FundingRateResponseSymbol(
					String symbol, double lastFundingRate, long nextFundingTime, long time
	) {
		public FundingRate get() {
			return new FundingRate(lastFundingRate, Instant.ofEpochMilli(nextFundingTime), Instant.ofEpochMilli(time));
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record FundingRateResponseSymbols(FundingRateResponseSymbol[] items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public FundingRateResponseSymbols {}

		public Map<String, FundingRate> get(List<String> symbolsToGet) {
			Map<String, FundingRate> fundingBySymbol = new HashMap<>();
			if (items == null) return fundingBySymbol;

			for (FundingRateResponseSymbol item : items) {
				if (item == null || item.symbol() == null) continue;
				if (!symbolsToGet.contains(item.symbol())) continue;
				fundingBySymbol.put(item.symbol(), item.get());
			}
			return fundingBySymbol;
		}
	}

	public record BookTickerResponseSymbol(
					String symbol, double bidPrice, double askPrice, double bidQty, double askQty, long time
	) {
		public BookTicker get() {
			return new BookTicker(bidPrice, bidQty, askPrice, askQty, Instant.ofEpochMilli(time));
		}
	}

	public record LotSizeResponseSymbol(SymbolInfo[] symbols) {
		public Double get(String symbol) {
			if (symbols == null) return null;

			for (SymbolInfo symbolInfo : symbols) {
				if (symbolInfo.symbol().equals(symbol)) {
					for (Filter filter : symbolInfo.filters()) {
						if (filter.filterType().equals("LOT_SIZE")) {
							return Double.parseDouble(filter.stepSize());
						}
					}
				}
			}
			return null;
		}
	}

	public record TradingVolume24hResponseSymbol(String symbol, double volume) {
		public double get() {
			return volume;
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	@JsonPropertyOrder({
					"openTime",
					"open",
					"high",
					"low",
					"close",
					"volume",
					"closeTime",
					"quoteVolume",
					"tradeCount",
					"takerBuyBase",
					"takerBuyQuote",
					"ignore"
	})
	private record TradingVolume1hItem(
					long openTime,
					String open,
					String high,
					String low,
					String close,
					String volume,
					long closeTime,
					String quoteVolume,
					int tradeCount,
					String takerBuyBase,
					String takerBuyQuote,
					String ignore
	) {
		public double get() {
			return Double.parseDouble(volume);
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record TradingVolume1hResponse(TradingVolume1hItem[] items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public TradingVolume1hResponse {}

		public double get() {
			if (items == null || items.length == 0) return 0.0;
			return items[0].get();
		}
	}
}
