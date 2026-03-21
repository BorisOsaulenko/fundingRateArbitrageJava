package com.boris.fundingarbitrage.exchange.impl.bitget.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	private static final BitgetChainsMap chainsMap = new BitgetChainsMap();

	public record ChangeLeverageResponse(String code, String msg, long requestTime) {
		public ChangeLeverageResponse {
			if (!"00000".equals(code)) {
				throw new IllegalStateException("Change maxLeverage failed: " + code + " " + msg);
			}
		}
	}

	public record SetMarginModeResponse(String code, String msg, long requestTime) {
		public SetMarginModeResponse {
			if (!"00000".equals(code)) {
				throw new IllegalStateException("Change margin mode failed: " + code + " " + msg);
			}
		}
	}

	public record SpotUsdtBalanceResponse(String code, String message, long requestTime, List<SpotBalanceEntry> data) {
		public BigDecimal get() {
			SpotBalanceEntry entry = data.getFirst();
			if (!"usdt".equalsIgnoreCase(entry.coin)) throw new IllegalStateException("Unexpected USDT balance entry");
			return entry.available;
		}

		private record SpotBalanceEntry(String coin, BigDecimal available) {
		}
	}

	private record FuturesAccount(
					String marginCoin, BigDecimal available
	) {
	}

	public record FuturesUsdtBalanceResponse(
					String code, String msg, long requestTime, List<FuturesAccount> data
	) {
		public BigDecimal get() {
			for (FuturesAccount account : data) {
				String marginCoin = account.marginCoin;
				if (!"USDT".equalsIgnoreCase(marginCoin)) continue;
				return account.available;
			}
			throw new IllegalStateException("USDT futures balance not found");
		}
	}

	private record ContractsItem(String symbol, int maxLever, BigDecimal makerFeeRate, BigDecimal takerFeeRate) {
	}

	public record ContractsResponse(String code, String msg, long requestTime, List<ContractsItem> data) {
		public Map<String, Integer> getMaxLeverages() {
			Map<String, Integer> maxLeverageBySymbol = new HashMap<>();
			for (ContractsItem item : data) maxLeverageBySymbol.put(item.symbol, item.maxLever);
			return maxLeverageBySymbol;
		}

		public Map<String, Fees> getFees() {
			Map<String, Fees> feesBySymbol = new HashMap<>();
			for (ContractsItem item : data) {
				Fees f = new Fees(
								item.makerFeeRate,
								item.takerFeeRate,
								item.makerFeeRate,
								item.takerFeeRate,
								Instant.ofEpochMilli(requestTime)
				);
				feesBySymbol.put(item.symbol, f);
			}

			return feesBySymbol;
		}
	}

	private record ChainInfo(
					String chain,
					Boolean withdrawable,
					Boolean rechargeable,
					BigDecimal withdrawFee,
					BigDecimal minWithdrawAmount,
					String withdrawMinScale
	) {
		int precisionPoints() {
			if (withdrawMinScale == null || withdrawMinScale.isEmpty()) {
				throw new IllegalStateException("Bitget withdrawMinScale missing for chain: " + chain);
			}
			int precision = Integer.parseInt(withdrawMinScale);
			if (precision <= 0) {
				throw new IllegalStateException("Bitget withdrawMinScale must be positive for chain: " + chain);
			}
			return precision;
		}
	}

	private record CoinInfo(
					String coin, List<ChainInfo> chains
	) {
	}

	public record SupportedChainsResponse(String code, String msg, long requestTime, List<CoinInfo> data) {
		public ExchangeChains get() {
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();

			CoinInfo entry = data.getFirst();
			String coinName = entry.coin;
			if (!"USDT".equalsIgnoreCase(coinName)) throw new IllegalStateException("Unexpected USDT coin name: " + coinName);

			List<ChainInfo> chains = entry.chains;
			if (chains == null) throw new IllegalStateException("USDT chains missing");

			for (ChainInfo chain : chains) {
				SupportedChain mapped = chainsMap.getInverse(chain.chain);
				if (mapped == null) continue;

				if (chain.rechargeable) builder.addDepositableChain(mapped);
				if (chain.withdrawable) {
					builder.addWithdrawableChain(new WithdrawChain(
									mapped,
									chain.withdrawFee(),
									chain.minWithdrawAmount(),
									chain.precisionPoints()
					));
				}
			}

			return builder.build();
		}
	}

	private record WalletAddressData(String address, String tag) {
	}

	public record UsdtWalletAddressResponse(
					String code, String msg, long requestTime, WalletAddressData data
	) {
		public WalletAddress get(SupportedChain chain) {
			if (data == null || data.address == null || data.address.isEmpty()) {
				throw new IllegalStateException("Bitget wallet address missing");
			}

			String address = data.address;
			String tag = data.tag;
			if (tag != null && tag.isEmpty()) tag = null;
			return new WalletAddress(chain, address, tag);
		}
	}

	private record WithdrawUsdtData(String orderId, String clientOid) {
	}

	public record WithdrawUsdtResponse(String code, String msg, long requestTime, WithdrawUsdtData data) {
	}

	private record PlaceOrderData(String orderId, String orderIdStr) {
	}

	public record PlaceFuturesOrderResponse(
					String code, String msg, long requestTime, PlaceOrderData data
	) {
		public String orderId() {
			if (data == null) return null;
			String id = data.orderId;
			if (id == null || id.isEmpty()) id = data.orderIdStr;
			return id;
		}
	}

	private record OrderRecordItem(
					String orderId,
					String symbol,
					BigDecimal size,
					BigDecimal baseVolume,
					BigDecimal tradeSize,
					BigDecimal price,
					BigDecimal tradePrice,
					BigDecimal fee,
					String feeCoin,
					long cTime
	) {
	}

	public record GetOrderRecordResponse(String code, String msg, long requestTime, List<OrderRecordItem> data) {
		public List<PartialFill> get() {
			if (data == null) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderRecordItem item : data) {
				String feeCoin = item.feeCoin;
				BigDecimal feeValue = "USDT".equalsIgnoreCase(feeCoin) ? item.fee() : null;
				Instant ts = Instant.ofEpochMilli(item.cTime());
				result.add(new PartialFill(item.orderId(), item.symbol(), item.baseVolume(), item.price(), feeValue, ts));
			}
			return result;
		}
	}

	private record InternalTransferData(String transferId, String status) {
	}

	public record InternalTransferResponse(
					String code, String msg, long requestTime, InternalTransferData data
	) {
		public InternalTransferResponse {
			if (!"00000".equals(code)) {
				throw new IllegalStateException("Change maxLeverage failed: " + code + " " + msg);
			}
		}
	}
}
