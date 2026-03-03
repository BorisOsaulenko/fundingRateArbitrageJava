package com.boris.fundingarbitrage.exchange.impl.okx.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	private record Instrument(String instId, BigDecimal lotSz) {
	}

	public record InstrumentsResponse(int code, String msg, List<Instrument> data) {
		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (Instrument item : data) result.put(item.instId(), item.lotSz());
			return result;
		}
	}

	private record TickerItem(
					String instId,
					BigDecimal bidPx,
					BigDecimal bidSz,
					BigDecimal askPx,
					BigDecimal askSz,
					BigDecimal volCcy24h,
					String ts
	) {
	}

	public record TickersResponse(int code, String msg, List<TickerItem> data) {
		public Map<String, BookTicker> getBookTickers() {
			Map<String, BookTicker> result = new HashMap<>();
			for (TickerItem item : data) {
				result.put(
								item.instId(), new BookTicker(
												item.bidPx(),
												item.bidSz(),
												item.askPx(),
												item.askSz(),
												Instant.ofEpochMilli(Long.parseLong(item.ts()))
								)
				);
			}
			return result;
		}

		public Map<String, BigDecimal> getVolume24h() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (TickerItem item : data) result.put(item.instId(), item.volCcy24h());
			return result;
		}
	}

	private record FundingRateItem(
					String instId, String fundingRate, String fundingTime, String nextFundingTime, String ts
	) {
	}

	public record FundingRatesResponse(int code, String msg, List<FundingRateItem> data) {
		public Map<String, FundingRate> getFundingRates() {
			Map<String, FundingRate> result = new HashMap<>();
			for (FundingRateItem item : data) {
				result.put(
								item.instId(), new FundingRate(
												new BigDecimal(item.fundingRate()),
												Instant.ofEpochMilli(Long.parseLong(item.fundingTime())),
												Instant.ofEpochMilli(Long.parseLong(item.ts()))
								)
				);
			}
			return result;
		}

		public Map<String, Integer> getFundingGranularityHours() {
			Map<String, Integer> result = new HashMap<>();
			for (FundingRateItem item : data) {
				long fundingTime = Long.parseLong(item.fundingTime());
				long nextFundingTime = Long.parseLong(item.nextFundingTime());
				result.put(item.instId(), (int) ((nextFundingTime - fundingTime) / 3_600_000L));
			}
			return result;
		}
	}
}
