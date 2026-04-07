package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.util.https.PaginatedResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	private static BigDecimal stepOrPrecision(BigDecimal step, BigDecimal precision) {
		if (step != null) return step;
		return precision;
	}

	public record InstrumentsInfoSymbolsResponse(
					int retCode, String retMsg, long time, InstrumentsInfoResult result
	) implements PaginatedResponse {
		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> resultMap = new HashMap<>();
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

		private record LotSizeFilter(BigDecimal qtyStep) {
		}

		private record InstrumentInfo(String symbol, LotSizeFilter lotSizeFilter, int fundingInterval, String status) {
		}

		private record InstrumentsInfoResult(String category, List<InstrumentInfo> list, String nextPageCursor) {
		}
	}

	public record TickersResponseSymbols(int retCode, String retMsg, long time, TickersResult result) {
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

		public Map<String, BigDecimal> getVolume24h() {
			Map<String, BigDecimal> resultMap = new HashMap<>();
			for (Ticker item : result.list()) resultMap.put(item.symbol(), item.volume24h());
			return resultMap;
		}

		public Map<String, Funding> getFundingRates() {
			Map<String, Funding> resultMap = new HashMap<>();
			for (Ticker item : result.list()) {
				Funding
								fr =
								new Funding(
												item.fundingRate(),
												Instant.ofEpochMilli(item.nextFundingTime()),
												Instant.ofEpochMilli(time)
								);
				resultMap.put(item.symbol(), fr);
			}
			return resultMap;
		}

		private record Ticker(
						String symbol,
						BigDecimal bid1Price,
						BigDecimal bid1Size,
						BigDecimal ask1Price,
						BigDecimal ask1Size,
						BigDecimal volume24h,
						BigDecimal fundingRate,
						long nextFundingTime
		) {
		}

		private record TickersResult(String category, List<Ticker> list) {
		}
	}

	public record SpotInstrumentsInfoResponse(
					int retCode, String retMsg, long time, SpotInstrumentsInfoResult result
	) {
		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> resultMap = new HashMap<>();
			for (SpotInstrumentInfo item : result.list()) {
				if (!"Trading".equalsIgnoreCase(item.status()) || !"USDT".equalsIgnoreCase(item.quoteCoin())) continue;
				resultMap.put(
								item.symbol(),
								stepOrPrecision(item.lotSizeFilter().qtyStep(), item.lotSizeFilter().basePrecision())
				);
			}
			return resultMap;
		}

		private record SpotLotSizeFilter(BigDecimal qtyStep, BigDecimal basePrecision) {
		}

		private record SpotInstrumentInfo(String symbol, String quoteCoin, String status, SpotLotSizeFilter lotSizeFilter) {
		}

		private record SpotInstrumentsInfoResult(String category, List<SpotInstrumentInfo> list) {
		}
	}

	public record SpotTickersResponse(int retCode, String retMsg, long time, SpotTickersResult result) {
		public Map<String, BookTicker> getBookTickers() {
			Map<String, BookTicker> resultMap = new HashMap<>();
			for (SpotTicker item : result.list()) {
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

		public Map<String, BigDecimal> getVolume24h() {
			Map<String, BigDecimal> resultMap = new HashMap<>();
			for (SpotTicker item : result.list()) resultMap.put(item.symbol(), item.turnover24h());
			return resultMap;
		}

		private record SpotTicker(
						String symbol,
						BigDecimal bid1Price,
						BigDecimal bid1Size,
						BigDecimal ask1Price,
						BigDecimal ask1Size,
						BigDecimal turnover24h
		) {
		}

		private record SpotTickersResult(String category, List<SpotTicker> list) {
		}
	}
}
