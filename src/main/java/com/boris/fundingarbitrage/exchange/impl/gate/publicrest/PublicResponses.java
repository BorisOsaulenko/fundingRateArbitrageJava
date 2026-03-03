package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record ContractsResponse(List<ContractResponse> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public ContractsResponse {
		}

		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (ContractResponse item : items) result.put(item.name(), item.quanto_multiplier());
			return result;
		}

		public Map<String, Integer> getFundingGranularityHours() {
			Map<String, Integer> result = new HashMap<>();
			for (ContractResponse item : items) result.put(item.name(), item.funding_interval() / 3600);
			return result;
		}

		public Map<String, FundingRate> getFundingRates() {
			Map<String, FundingRate> result = new HashMap<>();
			for (ContractResponse item : items) {
				FundingRate
								fr =
								new FundingRate(item.funding_rate(), Instant.ofEpochSecond(item.funding_next_apply()), Instant.now());
				result.put(item.name(), fr);
			}
			return result;
		}

		private record ContractResponse(
						String name,
						BigDecimal quanto_multiplier,
						BigDecimal funding_rate,
						long funding_next_apply,
						int funding_interval
		) {
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record TickersResponse(List<TickerResponse> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public TickersResponse {
		}

		public Map<String, BookTicker> getBookTickers() {
			Map<String, BookTicker> result = new HashMap<>();
			Instant now = Instant.now();
			for (TickerResponse item : items) {
				result.put(
								item.contract(),
								new BookTicker(item.highest_bid(), item.highest_size(), item.lowest_ask(), item.lowest_size(), now)
				);
			}
			return result;
		}

		public Map<String, BigDecimal> getVolume24h() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (TickerResponse item : items) result.put(item.contract(), item.volume_24h_quote());
			return result;
		}

		private record TickerResponse(
						String contract,
						BigDecimal volume_24h_quote,
						BigDecimal highest_bid,
						BigDecimal lowest_ask,
						BigDecimal highest_size,
						BigDecimal lowest_size
		) {
		}
	}
}
