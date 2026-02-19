package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.https.PaginatedResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	public record InstrumentsInfoSymbolsResponse(
					int retCode, String retMsg, long time, InstrumentsInfoResult result
	) implements PaginatedResponse {
		private record LotSizeFilter(double qtyStep) {}

		private record InstrumentInfo(String symbol, LotSizeFilter lotSizeFilter, int fundingInterval) {}

		private record InstrumentsInfoResult(String category, List<InstrumentInfo> list, String nextPageCursor) {}

		public Map<String, Double> getLotSizes() {
			Map<String, Double> resultMap = new HashMap<>();
			for (InstrumentInfo item : result.list()) resultMap.put(item.symbol(), item.lotSizeFilter().qtyStep());
			return resultMap;
		}

		public Map<String, Integer> getFundingGranularityHours() {
			Map<String, Integer> resultMap = new HashMap<>();
			for (InstrumentInfo item : result.list()) resultMap.put(item.symbol(), item.fundingInterval() / 60);
			return resultMap;
		}

		public String getPaginationIndex() {
			return result.nextPageCursor();
		}
	}

	public record TickersResponseSymbols(int retCode, String retMsg, long time, TickersResult result) {
		private record Ticker(
						String symbol,
						double bid1Price,
						double bid1Size,
						double ask1Price,
						double ask1Size,
						double volume24h,
						double fundingRate,
						long nextFundingTime
		) {}

		private record TickersResult(String category, List<Ticker> list) {}

		public Map<String, BookTicker> getBookTickers() {
			Map<String, BookTicker> resultMap = new HashMap<>();
			for (Ticker item : result.list()) {
				resultMap.put(
								item.symbol(), new BookTicker(
												item.bid1Price(),
												item.bid1Size(),
												item.ask1Price(),
												item.ask1Size(),
												Instant.ofEpochMilli(time)
								)
				);
			}
			return resultMap;
		}

		public Map<String, Double> getVolume24h() {
			Map<String, Double> resultMap = new HashMap<>();
			for (Ticker item : result.list()) resultMap.put(item.symbol(), item.volume24h());
			return resultMap;
		}

		public Map<String, FundingRate> getFundingRates() {
			Map<String, FundingRate> resultMap = new HashMap<>();
			for (Ticker item : result.list()) {
				FundingRate fr = new FundingRate(
								item.fundingRate(),
								Instant.ofEpochMilli(item.nextFundingTime()),
								Instant.ofEpochMilli(time)
				);
				resultMap.put(item.symbol(), fr);
			}
			return resultMap;
		}
	}
}
