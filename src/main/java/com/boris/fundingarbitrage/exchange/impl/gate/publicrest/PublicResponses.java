package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
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
	private static BigDecimal precisionToStep(Integer precision) {
		return BigDecimal.ONE.scaleByPowerOfTen(-precision);
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record ContractsResponse(List<ContractResponse> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public ContractsResponse {
		}

		public Map<String, FuturesTradingState> getTradingStates() {
			Map<String, FuturesTradingState> result = new HashMap<>();
			for (ContractResponse item : items) {
				if (item.in_delisting || !"trading".equalsIgnoreCase(item.status()))
					result.put(item.name(), FuturesTradingState.NOT_TRADING);
				else if (item.is_pre_market()) result.put(item.name(), FuturesTradingState.PREMARKET);
				else result.put(item.name(), FuturesTradingState.TRADING);
			}
			return result;
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
						int funding_interval,
						String status,
						boolean in_delisting,
						boolean is_pre_market
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

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SpotCurrencyPairsResponse(List<SpotCurrencyPair> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotCurrencyPairsResponse {
		}

		public Map<String, BigDecimal> getLotSizes() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (SpotCurrencyPair item : items) {
				if (!item.id().endsWith("_USDT") || !"tradable".equalsIgnoreCase(item.trade_status())) continue;
				result.put(item.id(), item.min_base_amount());
			}
			return result;
		}

		private record SpotCurrencyPair(
						String id,
						BigDecimal min_base_amount,
						Integer amount_precision,
						String trade_status
		) {
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SpotTickersResponse(List<SpotTickerResponse> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotTickersResponse {
		}

		public Map<String, BookTicker> getBookTickers() {
			Map<String, BookTicker> result = new HashMap<>();
			Instant now = Instant.now();
			for (SpotTickerResponse item : items) {
				result.put(
								item.currency_pair(),
								new BookTicker(item.highest_bid(), BigDecimal.TEN, item.lowest_ask(), BigDecimal.TEN, now)
				);
			}
			return result;
		}

		public Map<String, BigDecimal> getVolume24h() {
			Map<String, BigDecimal> result = new HashMap<>();
			for (SpotTickerResponse item : items) result.put(item.currency_pair(), item.quote_volume());
			return result;
		}

		private record SpotTickerResponse(
						String currency_pair,
						BigDecimal quote_volume,
						BigDecimal highest_bid,
						BigDecimal lowest_ask
		) {
		}
	}
}
