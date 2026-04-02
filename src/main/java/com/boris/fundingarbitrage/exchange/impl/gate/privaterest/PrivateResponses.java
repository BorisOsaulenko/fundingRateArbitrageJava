package com.boris.fundingarbitrage.exchange.impl.gate.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	private static final GateChainsMap chainsMap = new GateChainsMap();

	public record TradingFeesSymbolsResponse(
					BigDecimal taker_fee, BigDecimal maker_fee, BigDecimal futures_taker_fee, BigDecimal futures_maker_fee
	) {
		public Fees getAccountFees() {
			return new Fees(futures_maker_fee, futures_taker_fee, futures_maker_fee, futures_taker_fee, Instant.now());
		}

		public Fees getSpotAccountFees() {
			return new Fees(maker_fee, taker_fee, maker_fee, taker_fee, Instant.now());
		}
	}

	private record SpotBalanceItem(String currency, String available) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SpotUsdtBalanceResponse(List<SpotBalanceItem> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotUsdtBalanceResponse {
		}

		public BigDecimal get() {
			if (items == null) {
				throw new IllegalStateException("Balance info not found");
			}
			for (SpotBalanceItem item : items) {
				if (!"USDT".equalsIgnoreCase(item.currency)) continue;
				return new BigDecimal(item.available);
			}
			throw new IllegalStateException("USDT balance info not found");
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record FuturesUsdtBalanceResponse(String available) {
		public BigDecimal get() {
			if (available == null || available.isEmpty()) {
				throw new IllegalStateException("Balance info not found");
			}
			return new BigDecimal(available);
		}
	}

	private record MaxLeverageItem(String name, String leverage_max) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record MaxLeverageResponse(
					List<MaxLeverageItem> items
	) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public MaxLeverageResponse {
		}

		public Map<String, Integer> get() {
			Map<String, Integer> result = new java.util.HashMap<>();
			for (MaxLeverageItem item : items) result.put(item.name(), Integer.parseInt(item.leverage_max));
			return result;
		}
	}

	public record ChainEntry(
					String chain,
					String decimal,
					int is_disabled,
					int is_deposit_disabled,
					int is_withdraw_disabled,
					int is_tag
	) {
		public int precisionPoints() {
			if (decimal == null || decimal.isEmpty()) {
				throw new IllegalStateException("Gate decimal missing for chain: " + chain);
			}
			int precision = Integer.parseInt(decimal);
			if (precision <= 0) {
				throw new IllegalStateException("Gate decimal must be positive for chain: " + chain);
			}
			return precision;
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record SupportedChainsResponse(ChainEntry[] chains) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SupportedChainsResponse {
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MultichainAddress(String chain, String address, String memo) {
	}

	public record UsdtWalletAddressResponse(
					String currency, String address, String memo, List<MultichainAddress> multichain_addresses
	) {
		public WalletAddress get(SupportedChain chain) {
			String addr = address;
			String tag = memo;
			if (multichain_addresses != null) {
				for (MultichainAddress entry : multichain_addresses) {
					SupportedChain mapped = chainsMap.getInverse(entry.chain);
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

	public record WithdrawUsdtResponse(String id) {
	}

	public record PlaceFuturesOrderResponse(String id) {
		public String orderId() {
			return id;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OrderRecordItem(
					String order_id, String contract, String size, String price, String fee, long create_time
	) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record GetOrderRecordResponse(List<OrderRecordItem> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public GetOrderRecordResponse {
		}

		public List<PartialFill> get() {
			if (items == null) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderRecordItem item : items) {
				String orderId = item.order_id;
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.contract;
				BigDecimal size = new BigDecimal(item.size);
				BigDecimal price = new BigDecimal(item.price);
				BigDecimal fee = new BigDecimal(item.fee);
				Instant ts = Instant.ofEpochSecond(item.create_time);
				result.add(new PartialFill(orderId, symbol, size.abs(), price, fee, ts));
			}
			return result;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SpotOrderRecordItem(
					String order_id,
					String currency_pair,
					String amount,
					String price,
					String fee,
					String fee_currency,
					String create_time_ms
	) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record GetSpotOrderRecordResponse(List<SpotOrderRecordItem> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public GetSpotOrderRecordResponse {
		}

		public List<PartialFill> get() {
			if (items == null) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (SpotOrderRecordItem item : items) {
				String orderId = item.order_id;
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.currency_pair;
				if (symbol == null || symbol.isEmpty()) continue;
				BigDecimal amount = new BigDecimal(item.amount);
				BigDecimal price = new BigDecimal(item.price);
				BigDecimal fee = null;
				if (item.fee != null && !item.fee.isEmpty() && "USDT".equalsIgnoreCase(item.fee_currency)) {
					fee = new BigDecimal(item.fee).abs();
				}
				Instant ts = Instant.ofEpochMilli(Long.parseLong(item.create_time_ms));
				result.add(new PartialFill(orderId, symbol, amount.abs(), price, fee, ts));
			}
			return result;
		}
	}

	public record InternalTransferResponse(String id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WithdrawalFeeItem(
					String currency, String withdraw_amount_mini, Map<String, BigDecimal> withdraw_fix_on_chains
	) {
	}

	@JsonFormat(shape = JsonFormat.Shape.ARRAY)
	public record WithdrawalFeeResponse(List<WithdrawalFeeItem> items) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public WithdrawalFeeResponse {
		}

		public BigDecimal getMinWithdraw() {
			for (WithdrawalFeeItem item : items) {
				if (!"USDT".equalsIgnoreCase(item.currency)) continue;
				String minWithdraw = item.withdraw_amount_mini;

				if (minWithdraw == null || minWithdraw.isEmpty()) {
					throw new IllegalStateException("Minimum withdrawal amount not found");
				}

				return new BigDecimal(minWithdraw);
			}

			throw new IllegalStateException("USDT withdrawal fee info not found");
		}

		public BigDecimal getFeeForChain(SupportedChain chain) {
			if (items == null) throw new IllegalStateException("Withdrawal fees info not found");
			for (WithdrawalFeeItem item : items) {
				if (!"USDT".equalsIgnoreCase(item.currency)) continue;
				Map<String, BigDecimal> chains = item.withdraw_fix_on_chains;
				if (chains == null) throw new IllegalStateException("Withdrawal fees chains info not found");

				String gateChain = chainsMap.get(chain);
				String key = chains.keySet().stream().filter(gateChain::equalsIgnoreCase).findFirst().orElse(null);
				if (key == null) throw new IllegalStateException("Withdrawal fee not found for chain: " + gateChain);

				return chains.get(key);
			}

			throw new IllegalStateException("USDT withdrawal fee info not found");
		}
	}
}
