package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PublicResponses {

	public record ExchangeInfoResponse(List<SymbolInfo> symbols) {
		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();

			for (SymbolInfo info : symbols) {
				if (!"TRADING".equalsIgnoreCase(info.status()) || !"PERPETUAL".equalsIgnoreCase(info.contractType())) continue;
				for (Filter filter : info.filters()) {
					if (filter.filterType().equals("LOT_SIZE")) {
						result.put(info.symbol(), filter.stepSize());
					}
				}
			}

			return result;
		}

		public List<String> getExistingSymbols() {
			List<String> result = new ArrayList<>();
			for (SymbolInfo info : symbols) result.add(info.symbol());
			return result;
		}

		private record Filter(String filterType, String minQty, String maxQty, BigDecimal stepSize) {}

		private record SymbolInfo(String symbol, String status, String contractType, Filter[] filters) {}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record PremiumIndexResponse(List<FundingRateResponseSymbol> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public PremiumIndexResponse {}

		public Map<String, FundingRate> getFundingRates() {
			Map<String, FundingRate> fundingBySymbol = new HashMap<>();
			for (FundingRateResponseSymbol item : items) {
				FundingRate fr = new FundingRate(
								item.lastFundingRate(),
								Instant.ofEpochMilli(item.nextFundingTime),
								Instant.ofEpochMilli(item.time())
				);
				fundingBySymbol.put(item.symbol(), fr);
			}
			return fundingBySymbol;
		}

		private record FundingRateResponseSymbol(
						String symbol, double lastFundingRate, long nextFundingTime, long time
		) {}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record BookTickerResponse(List<BookTickerResponseSymbol> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public BookTickerResponse {}

		public Map<String, BookTicker> getBookTickers() {
			Map<String, BookTicker> result = new HashMap<>();
			items.forEach(item -> {
				BookTicker ticker = new BookTicker(
								item.bidPrice,
								item.bidQty,
								item.askPrice,
								item.askQty,
								Instant.ofEpochMilli(item.time())
				);
				result.put(item.symbol(), ticker);
			});
			return result;
		}

		public record BookTickerResponseSymbol(
						String symbol, double bidPrice, double askPrice, double bidQty, double askQty, long time
		) {}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record Statistics24hResponse(List<Statistics24hItem> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public Statistics24hResponse {}

		public Map<String, Double> getVolume24h() {
			Map<String, Double> result = new HashMap<>();
			for (Statistics24hItem item : items) result.put(item.symbol(), item.volume());
			return result;
		}

		private record Statistics24hItem(String symbol, double volume) {}
	}


	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record FundingInfoResponse(List<FundingGranularityEntry> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public FundingInfoResponse {}

		public Map<String, Integer> getFundingGranularities() {
			Map<String, Integer> result = new HashMap<>();
			for (var item : items) result.put(item.symbol(), item.fundingIntervalHours());
			return result;
		}

		private record FundingGranularityEntry(String symbol, int fundingIntervalHours) {}
	}
}
