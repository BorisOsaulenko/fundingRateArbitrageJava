package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	public record ContractsResponse(String code, String msg, long requestTime, List<Contract> data) {
		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (Contract contract : data) result.put(contract.symbol(), contract.sizeMultiplier());
			return result;
		}

		private record Contract(String symbol, BigDecimal sizeMultiplier) {
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
}
