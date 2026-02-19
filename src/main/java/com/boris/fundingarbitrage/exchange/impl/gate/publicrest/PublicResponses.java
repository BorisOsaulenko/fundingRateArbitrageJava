package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PublicResponses {
	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record ContractsResponse(List<ContractResponse> items) {
		private record ContractResponse(
						String name, double quanto_multiplier, double funding_rate, long funding_next_apply, int funding_interval
		) {}

		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public ContractsResponse {}

		public Map<String, Double> getLotSizes() {
			Map<String, Double> result = new HashMap<>();
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
				FundingRate fr = new FundingRate(
								item.funding_rate(),
								Instant.ofEpochSecond(item.funding_next_apply()),
								Instant.now()
				);
				result.put(item.name(), fr);
			}
			return result;
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record TickersResponse(List<TickerResponse> items) {
		private record TickerResponse(
						String contract,
						double volume_24h_quote,
						double highest_bid,
						double lowest_ask,
						double highest_size,
						double lowest_size
		) {}

		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public TickersResponse {}

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

		public Map<String, Double> getVolume24h() {
			Map<String, Double> result = new HashMap<>();
			for (TickerResponse item : items) result.put(item.contract(), item.volume_24h_quote());
			return result;
		}
	}
}
