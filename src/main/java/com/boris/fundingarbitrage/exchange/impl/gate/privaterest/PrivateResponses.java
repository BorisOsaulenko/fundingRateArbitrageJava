package com.boris.fundingarbitrage.exchange.impl.gate.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrivateResponses {
	public record TradingFeesSymbolsResponse(
					double taker_fee, double maker_fee, double futures_taker_fee, double futures_maker_fee
	) {
		public Fees getAccountFees() {
			return new Fees(futures_maker_fee, futures_taker_fee, futures_maker_fee, futures_taker_fee, Instant.now());
		}
	}

	public record ChangeLeverageResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public ChangeLeverageResponse {}
	}

	public record SetMarginModeResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SetMarginModeResponse {}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SpotUsdtBalanceResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotUsdtBalanceResponse {}

		public double get() {
			if (node == null || !node.isArray()) {
				throw new IllegalStateException("Balance info not found");
			}
			for (JsonNode item : node) {
				String currency = item.path("currency").asText();
				if (!"USDT".equalsIgnoreCase(currency)) continue;
				return Double.parseDouble(item.path("available").asText());
			}
			throw new IllegalStateException("USDT balance info not found");
		}
	}

	public record FuturesUsdtBalanceResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public FuturesUsdtBalanceResponse {}

		public double get() {
			if (node == null || !node.isObject()) {
				throw new IllegalStateException("Balance info not found");
			}
			return Double.parseDouble(node.path("available").asText());
		}
	}

	private record MaxLeverageItem(String name, String leverage_max) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record MaxLeverageResponse(
					List<MaxLeverageItem> items
	) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public MaxLeverageResponse {}

		public Map<String, Integer> get() {
			Map<String, Integer> result = new HashMap<>();
			for (MaxLeverageItem item : items) result.put(item.name(), Integer.parseInt(item.leverage_max));
			return result;
		}
	}

	public record ChainEntry(
					String chain, int is_disabled, int is_deposit_disabled, int is_withdraw_disabled, int is_tag
	) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SupportedChainsResponse(ChainEntry[] chains) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SupportedChainsResponse {}
	}

	public record UsdtWalletAddressResponse(
					String currency, String address, String memo, JsonNode multichain_addresses
	) {
		public WalletAddress get(SupportedChain chain) {
			String addr = address;
			String tag = memo;
			if (multichain_addresses != null && multichain_addresses.isArray()) {
				for (JsonNode entry : multichain_addresses) {
					String chainName = entry.path("chain").asText();
					SupportedChain mapped = ChainsMap.getInverse(chainName);
					if (mapped == chain) {
						addr = entry.path("address").asText();
						tag = entry.path("memo").asText(null);
						break;
					}
				}
			}
			if (tag != null && tag.isEmpty()) tag = null;
			return new WalletAddress(chain, addr, tag);
		}
	}

	public record WithdrawUsdtResponse(String id) {}

	public record PlaceFuturesOrderResponse(String id) {
		public String orderId() {
			return id;
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record GetOrderRecordResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public GetOrderRecordResponse {}

		public List<PartialFill> get() {
			if (node == null || !node.isArray()) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (JsonNode item : node) {
				String orderId = item.path("order_id").asText();
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.path("contract").asText();
				double size = Double.parseDouble(item.path("size").asText());
				double price = Double.parseDouble(item.path("price").asText());
				double fee = Double.parseDouble(item.path("fee").asText());
				Instant ts = Instant.ofEpochSecond((item.path("create_time").asLong()));
				result.add(new PartialFill(orderId, symbol, Math.abs(size), price, fee, ts));
			}
			return result;
		}
	}

	public record InternalTransferResponse(String id) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record WithdrawalFeeResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public WithdrawalFeeResponse {}

		public double getMinWithdraw() {
			if (node == null || !node.isArray()) {
				throw new IllegalStateException("Withdrawal fees info not found");
			}

			for (JsonNode item : node) {
				String currency = item.path("currency").asText();
				if (!"USDT".equalsIgnoreCase(currency)) continue;
				String minWithdraw = item.path("withdraw_amount_mini").asText();

				if (minWithdraw == null || minWithdraw.isEmpty()) {
					throw new IllegalStateException("Minimum withdrawal amount not found");
				}

				return Double.parseDouble(minWithdraw);
			}

			throw new IllegalStateException("USDT withdrawal fee info not found");
		}

		public double getFeeForChain(SupportedChain chain) {
			if (node == null || !node.isArray()) {
				throw new IllegalStateException("Withdrawal fees info not found");
			}

			for (JsonNode item : node) {
				String currency = item.path("currency").asText();
				if (!"USDT".equalsIgnoreCase(currency)) continue;
				JsonNode chains = item.path("withdraw_fix_on_chains");
				if (chains == null) {
					throw new IllegalStateException("Withdrawal fees chains info not found");
				}

				String gateChain = ChainsMap.get(chain);
				String fee = chains.path(gateChain).asText();

				if (fee == null || fee.isEmpty()) {
					throw new IllegalStateException("Withdrawal fee not found for chain: " + gateChain);
				}

				return Double.parseDouble(fee);
			}

			throw new IllegalStateException("USDT withdrawal fee info not found");
		}
	}
}
