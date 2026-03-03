package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	private record ActiveContract(
					String symbol,
					BigDecimal multiplier,
					BigDecimal volumeOf24h,
					BigDecimal fundingFeeRate,
					long nextFundingRateDateTime,
					long fundingRateGranularity
	) {
	}

	public record ActiveContractsResponse(String code, String msg, List<ActiveContract> data) {
		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (ActiveContract contract : data) result.put(contract.symbol(), contract.multiplier());
			return result;
		}

		public Map<String, BigDecimal> getVolume24h() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (ActiveContract contract : data) result.put(contract.symbol(), contract.volumeOf24h());
			return result;
		}

		public Map<String, Integer> getFundingGranularityHours() {
			Map<String, Integer> result = new HashMap<>();
			for (ActiveContract contract : data) {
				result.put(contract.symbol(), (int) (contract.fundingRateGranularity() / 3_600_000L));
			}
			return result;
		}

		public Map<String, FundingRate> getFundingRates() {
			Map<String, FundingRate> result = new HashMap<>();
			Instant now = Instant.now();
			for (ActiveContract contract : data) {
				result.put(
								contract.symbol(),
								new FundingRate(
												contract.fundingFeeRate(),
												Instant.ofEpochMilli(contract.nextFundingRateDateTime()),
												now
								)
				);
			}
			return result;
		}
	}

	private record Ticker(
					String symbol,
					BigDecimal bestBidPrice,
					BigDecimal bestBidSize,
					BigDecimal bestAskPrice,
					BigDecimal bestAskSize,
					long ts
	) {
	}

	public record AllTickersResponse(String code, String msg, List<Ticker> data) {
		public Map<String, BookTicker> getBookTickers() {
			Map<String, BookTicker> result = new HashMap<>();
			for (Ticker ticker : data) {
				result.put(
								ticker.symbol(), new BookTicker(
												ticker.bestBidPrice(),
												ticker.bestBidSize(),
												ticker.bestAskPrice(),
												ticker.bestAskSize(),
												Instant.ofEpochMilli(ticker.ts() / 1_000_000L)
								)
				);
			}
			return result;
		}
	}

	private record WsServer(String endpoint) {
	}

	private record PublicWsTokenData(String token, List<WsServer> instanceServers) {
	}

	public record PublicWsTokenResponse(String code, String msg, PublicWsTokenData data) {
		public String token() {
			return data.token();
		}

		public String endpoint() {
			return data.instanceServers().get(0).endpoint();
		}
	}
}
