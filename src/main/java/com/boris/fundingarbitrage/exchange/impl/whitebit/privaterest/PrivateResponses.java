package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	private static final WhitebitChainsMap chainsMap = new WhitebitChainsMap();

	public record TradingFeesSymbolsResponse(
					BigDecimal maker, BigDecimal taker, BigDecimal futures_maker, BigDecimal futures_taker
	) {
		public Fees getAccountFees() {
			BigDecimal divider = BigDecimal.valueOf(100);
			BigDecimal maker = futures_maker.divide(divider);
			BigDecimal taker = futures_taker.divide(divider);
			return new Fees(maker, taker, maker, taker, Instant.now());
		}

		public Fees getSpotFees() {
			BigDecimal divider = BigDecimal.valueOf(100);
			BigDecimal makerFee = maker.divide(divider);
			BigDecimal takerFee = taker.divide(divider);
			return new Fees(makerFee, takerFee, makerFee, takerFee, Instant.now());
		}
	}

	public record SpotBalanceResponse(BigDecimal main_balance) {
		public BigDecimal usdtAvailable() {
			return main_balance;
		}
	}

	public record CollateralSummaryResponse(BigDecimal USDT) {
		public BigDecimal futuresBalance() {
			return USDT;
		}
	}

	public record SupportedChainsResponseEntry(
					String ticker, boolean is_api_depositable, boolean is_api_withdrawal, SupportedChainsWithdrawInfo withdraw
	) {
	}

	public record SupportedChainsWithdrawInfo(BigDecimal fixed, BigDecimal min_amount) {
	}

	public record SupportedChainsResponse(Map<String, SupportedChainsResponseEntry> entries) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SupportedChainsResponse {
		}

		private String extractNetwork(String key) {
			int start = key.indexOf('(');
			int end = key.indexOf(')');
			if (start < 0 || end <= start) return null;
			return key.substring(start + 1, end).trim();
		}

		public ExchangeChains getChains() {
			List<SupportedChain> depositable = new ArrayList<>();
			List<WithdrawChain> withdrawable = new ArrayList<>();

			for (var entry : entries.entrySet()) {
				if (!"USDT".equalsIgnoreCase(entry.getValue().ticker())) continue;
				SupportedChain chain = chainsMap.getInverse(extractNetwork(entry.getKey()));
				if (chain == null) continue;

				if (entry.getValue().is_api_depositable()) depositable.add(chain);
				if (entry.getValue().is_api_withdrawal()) {
					BigDecimal minWidthdraw = entry.getValue().withdraw().min_amount();
					BigDecimal fee = entry.getValue().withdraw().fixed();
					withdrawable.add(new WithdrawChain(chain, fee, minWidthdraw, 6)); // Whitebit usdt precision = 6 on all chains
				}
			}

			return new ExchangeChains(depositable, withdrawable);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WalletAccount(String address, String memo) {
	}

	public record WalletAddressResponse(WalletAccount account) {
		public WalletAddress get(SupportedChain chain) {
			if (account == null) throw new IllegalStateException("Account info missing");
			String address = account.address;
			if (address == null || address.isEmpty()) throw new IllegalStateException("Missing address");
			String memo = account.memo;
			if (memo != null && memo.isEmpty()) memo = null;
			return new WalletAddress(chain, address, memo);
		}
	}

	public record PlaceOrderResponse(String orderId) {
		public String orderId() {
			if (orderId == null || orderId.isEmpty()) throw new IllegalStateException("Missing orderId");
			return orderId;
		}
	}

	public record PlaceSpotOrderResponse(String orderId) {
		public String orderId() {
			if (orderId == null || orderId.isEmpty()) throw new IllegalStateException("Missing orderId");
			return orderId;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OrderDealRecord(
					String dealOrderId,
					String market,
					BigDecimal amount,
					BigDecimal price,
					BigDecimal fee,
					String feeAsset,
					long time
	) {
	}

	public record OrderDealsResponse(List<OrderDealRecord> records) {
		public List<PartialFill> get() {
			if (records == null) {
				throw new IllegalStateException("Order deals records missing");
			}
			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderDealRecord item : records) {
				String orderId = item.dealOrderId;
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.market;
				if (symbol == null || symbol.isEmpty()) throw new IllegalStateException("Missing market");
				BigDecimal feeValue = "USDT".equalsIgnoreCase(item.feeAsset) ? item.fee : null;
				Instant ts = Instant.ofEpochSecond(item.time);
				result.add(new PartialFill(orderId, symbol, item.amount, item.price, feeValue, ts));
			}
			return result;
		}
	}

	private record MaxLeverageResponseEntry(String ticker_id, int max_leverage) {
	}

	public record MaxLeverageResponse(boolean success, String message, List<MaxLeverageResponseEntry> result) {
		public Map<String, Integer> get() {
			Map<String, Integer> maxLeveragesBySymbol = new HashMap<>();
			for (var item : result) maxLeveragesBySymbol.put(item.ticker_id, item.max_leverage);
			return maxLeveragesBySymbol;
		}
	}

	public record TokenResponse(String websocket_token) {
	}
}
