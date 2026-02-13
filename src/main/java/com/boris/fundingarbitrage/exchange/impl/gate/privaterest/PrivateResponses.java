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
	public record TradingFeesResponse(
					String currency,
					String taker_fee,
					String maker_fee,
					String futures_taker_fee,
					String futures_maker_fee
	) {
		public Fees getFees() {
			double maker = futures_maker_fee == null || futures_maker_fee.isEmpty() ? 0.0 : Double.parseDouble(
							futures_maker_fee);
			double taker = futures_taker_fee == null || futures_taker_fee.isEmpty() ? 0.0 : Double.parseDouble(
							futures_taker_fee);
			if (maker == 0.0 && taker == 0.0) {
				maker = maker_fee == null || maker_fee.isEmpty() ? 0.0 : Double.parseDouble(maker_fee);
				taker = taker_fee == null || taker_fee.isEmpty() ? 0.0 : Double.parseDouble(taker_fee);
			}
			return new Fees(maker, taker, maker, taker, Instant.now());
		}
	}

	public record TradingFeesSymbolsResponse(
					String currency,
					String taker_fee,
					String maker_fee,
					String futures_taker_fee,
					String futures_maker_fee
	) {
		public Map<String, Fees> getFeesBySymbols(List<String> symbols) {
			Map<String, Fees> feesBySymbol = new HashMap<>();
			Fees fees = new TradingFeesResponse(
							currency,
							taker_fee,
							maker_fee,
							futures_taker_fee,
							futures_maker_fee
			).getFees();
			for (String symbol : symbols) {
				feesBySymbol.put(symbol, fees);
			}
			return feesBySymbol;
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

	public record MaxLeverageResponse(
					String leverage_max
	) {
		public int get() {
			if (leverage_max == null || leverage_max.isEmpty()) {
				throw new IllegalStateException("Leverage info not found");
			}
			return (int) Math.round(Double.parseDouble(leverage_max));
		}
	}

	public record ChainEntry(
					String chain,
					int is_disabled,
					int is_deposit_disabled,
					int is_withdraw_disabled,
					int is_tag
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
