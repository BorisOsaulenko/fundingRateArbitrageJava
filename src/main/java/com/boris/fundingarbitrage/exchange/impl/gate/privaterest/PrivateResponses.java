package com.boris.fundingarbitrage.exchange.impl.gate.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	public record TradingFeesSymbolsResponse(
					double taker_fee, double maker_fee, double futures_taker_fee, double futures_maker_fee
	) {
		public Fees getAccountFees() {
			return new Fees(futures_maker_fee, futures_taker_fee, futures_maker_fee, futures_taker_fee, Instant.now());
		}
	}

	private record SpotBalanceItem(String currency, String available) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SpotUsdtBalanceResponse(List<SpotBalanceItem> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotUsdtBalanceResponse {}

		public double get() {
			if (items == null) {
				throw new IllegalStateException("Balance info not found");
			}
			for (SpotBalanceItem item : items) {
				if (!"USDT".equalsIgnoreCase(item.currency)) continue;
				return Double.parseDouble(item.available);
			}
			throw new IllegalStateException("USDT balance info not found");
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record FuturesUsdtBalanceResponse(String available) {
		public double get() {
			if (available == null || available.isEmpty()) {
				throw new IllegalStateException("Balance info not found");
			}
			return Double.parseDouble(available);
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
			Map<String, Integer> result = new java.util.HashMap<>();
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MultichainAddress(String chain, String address, String memo) {}

	public record UsdtWalletAddressResponse(
					String currency, String address, String memo, List<MultichainAddress> multichain_addresses
	) {
		public WalletAddress get(SupportedChain chain) {
			String addr = address;
			String tag = memo;
			if (multichain_addresses != null) {
				for (MultichainAddress entry : multichain_addresses) {
					SupportedChain mapped = ChainsMap.getInverse(entry.chain);
					if (mapped == chain) {
						addr = entry.address;
						tag = entry.memo;
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OrderRecordItem(
					String order_id, String contract, String size, String price, String fee, long create_time
	) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record GetOrderRecordResponse(List<OrderRecordItem> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public GetOrderRecordResponse {}

		public List<PartialFill> get() {
			if (items == null) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderRecordItem item : items) {
				String orderId = item.order_id;
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.contract;
				double size = Double.parseDouble(item.size);
				double price = Double.parseDouble(item.price);
				double fee = Double.parseDouble(item.fee);
				Instant ts = Instant.ofEpochSecond(item.create_time);
				result.add(new PartialFill(orderId, symbol, Math.abs(size), price, fee, ts));
			}
			return result;
		}
	}

	public record InternalTransferResponse(String id) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WithdrawalFeeItem(
					String currency, String withdraw_amount_mini, Map<String, String> withdraw_fix_on_chains
	) {}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record WithdrawalFeeResponse(List<WithdrawalFeeItem> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public WithdrawalFeeResponse {}

		public double getMinWithdraw() {
			if (items == null) {
				throw new IllegalStateException("Withdrawal fees info not found");
			}

			for (WithdrawalFeeItem item : items) {
				if (!"USDT".equalsIgnoreCase(item.currency)) continue;
				String minWithdraw = item.withdraw_amount_mini;

				if (minWithdraw == null || minWithdraw.isEmpty()) {
					throw new IllegalStateException("Minimum withdrawal amount not found");
				}

				return Double.parseDouble(minWithdraw);
			}

			throw new IllegalStateException("USDT withdrawal fee info not found");
		}

		public double getFeeForChain(SupportedChain chain) {
			if (items == null) {
				throw new IllegalStateException("Withdrawal fees info not found");
			}

			for (WithdrawalFeeItem item : items) {
				if (!"USDT".equalsIgnoreCase(item.currency)) continue;
				Map<String, String> chains = item.withdraw_fix_on_chains;
				if (chains == null) {
					throw new IllegalStateException("Withdrawal fees chains info not found");
				}

				String gateChain = ChainsMap.get(chain);
				String fee = chains.get(gateChain);

				if (fee == null || fee.isEmpty()) {
					throw new IllegalStateException("Withdrawal fee not found for chain: " + gateChain);
				}

				return Double.parseDouble(fee);
			}

			throw new IllegalStateException("USDT withdrawal fee info not found");
		}
	}
}
