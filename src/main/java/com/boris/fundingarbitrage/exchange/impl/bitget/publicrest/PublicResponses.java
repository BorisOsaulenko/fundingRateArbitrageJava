package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	private static BigDecimal precisionToStep(String precision) {
		return BigDecimal.ONE.scaleByPowerOfTen(-Integer.parseInt(precision));
	}

	public record ContractsResponse(String code, String msg, long requestTime, List<Contract> data) {
		private static FuturesTradingState toTradingState(String symbolStatus) {
			if ("normal".equalsIgnoreCase(symbolStatus)) return FuturesTradingState.TRADING;
			return FuturesTradingState.NOT_TRADING;
		}

		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (Contract contract : data) result.put(contract.symbol(), contract.sizeMultiplier());
			return result;
		}

		public Map<String, FuturesTradingState> getTradingStates() {
			Map<String, FuturesTradingState> result = new HashMap<>();
			for (Contract contract : data) {
				result.put(contract.symbol(), toTradingState(contract.symbolStatus()));
			}
			return result;
		}

		private record Contract(String symbol, BigDecimal sizeMultiplier, String symbolStatus) {
		}
	}

	public record TickerResponse(String code, String msg, long requestTime, List<Ticker> data) {
		public Map<String, BookTicker> getBookTickers() {
			Map<String, BookTicker> result = new HashMap<>();
			for (Ticker item : data) {
				BookTicker
								bt =
								new BookTicker(item.bidPr, item.bidSz, item.askPr, item.askSz, Instant.ofEpochMilli(requestTime));
				result.put(item.symbol, bt);
			}
			return result;
		}

		public Map<String, BigDecimal> getUsdtVolumes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (Ticker item : data) result.put(item.symbol, item.usdtVolume);
			return result;
		}

		private record Ticker(
						String symbol, BigDecimal bidPr, BigDecimal askPr, BigDecimal bidSz, BigDecimal askSz, BigDecimal usdtVolume
		) {
		}
	}

	public record CurrentFundingRateResponse(
					String code, String msg, long requestTime, List<FundingRateItem> data
	) {
		public Map<String, Integer> getFundingGranularity() {
			Map<String, Integer> result = new HashMap<>();
			for (var ticker : data) result.put(ticker.symbol(), Integer.parseInt(ticker.fundingRateInterval()));
			return result;
		}

		public Map<String, FundingRate> getFundingRates() {
			Map<String, FundingRate> result = new HashMap<>();
			for (var ticker : data) {
				FundingRate fr = new FundingRate(ticker.fundingRate(), Instant.ofEpochMilli(ticker.nextUpdate), Instant.now());
				result.put(ticker.symbol(), fr);
			}
			return result;
		}

		private record FundingRateItem(
						String symbol, String fundingRateInterval, BigDecimal fundingRate, long nextUpdate
		) {
		}
	}

	public record SpotSymbolsResponse(String code, String msg, long requestTime, List<SpotSymbol> data) {
		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (SpotSymbol symbol : data) {
				if (!"USDT".equalsIgnoreCase(symbol.quoteCoin()) || !"online".equalsIgnoreCase(symbol.status())) continue;
				result.put(symbol.symbol(), precisionToStep(symbol.quantityPrecision()));
			}
			return result;
		}

		private record SpotSymbol(String symbol, String quoteCoin, String quantityPrecision, String status) {
		}
	}
}
