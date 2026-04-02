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
				throw new IllegalStateException("Change leverage failed: " + code + " " + msg);
			}
		}
	}

	public record SpotUsdtBalanceResponse(String code, String msg, long requestTime, List<FundingAsset> data) {
		public BigDecimal get() {
			if (data == null || data.isEmpty()) throw new IllegalStateException("USDT funding balance missing");
			for (FundingAsset entry : data) {
				if ("USDT".equalsIgnoreCase(entry.coin)) return entry.available;
			}
			throw new IllegalStateException("USDT funding balance not found");
		}

		private record FundingAsset(String coin, BigDecimal available) {
		}
	}

	private record AccountAssetsData(List<AccountAsset> assets) {
	}

	private record AccountAsset(String coin, BigDecimal available) {
	}

	public record FuturesUsdtBalanceResponse(
					String code, String msg, long requestTime, AccountAssetsData data
	) {
		public BigDecimal get() {
			if (data == null || data.assets == null) throw new IllegalStateException("Account assets missing");
			for (AccountAsset account : data.assets) {
				String coin = account.coin;
				if (!"USDT".equalsIgnoreCase(coin)) continue;
				return account.available;
			}
			throw new IllegalStateException("USDT account balance not found");
		}
	}

	private record TradingFeeData(String symbol, BigDecimal takerFeeRate, BigDecimal makerFeeRate) {
	}

	public record SpotTradingFeesResponse(String code, String msg, long requestTime, List<TradingFeeData> data) {
		public Map<String, Fees> getFees() {
			if (!"00000".equals(code))
				throw new IllegalStateException("Bitget spot trading fees failed: " + code + " " + msg);

			Map<String, Fees> result = new HashMap<>();
			for (TradingFeeData entry : data) {
				Fees f = new Fees(
								entry.makerFeeRate,
								entry.takerFeeRate,
								entry.makerFeeRate,
								entry.takerFeeRate,
								Instant.ofEpochMilli(requestTime)
				);
				result.put(entry.symbol, f);
			}

			return result;
		}
	}

	private record ContractsItem(String symbol, String maxLeverage, BigDecimal makerFeeRate, BigDecimal takerFeeRate) {
	}

	public record ContractsResponse(String code, String msg, long requestTime, List<ContractsItem> data) {
		public Map<String, Integer> getMaxLeverages() {
			Map<String, Integer> maxLeverageBySymbol = new HashMap<>();
			for (ContractsItem item : data) {
				int max = new BigDecimal(item.maxLeverage).intValue();
				maxLeverageBySymbol.put(item.symbol, max);
			}
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

	private record OrderRecordFeeDetail(
					String feeCoin,
					BigDecimal fee
	) {
	}

	private record OrderRecordItem(
					String orderId,
					String symbol,
					BigDecimal execQty,
					BigDecimal execPrice,
					List<OrderRecordFeeDetail> feeDetail,
					long createdTime
	) {
	}

	private record OrderRecordData(
					List<OrderRecordItem> list
	) {
	}

	public record GetOrderRecordResponse(String code, String msg, long requestTime, OrderRecordData data) {
		public List<PartialFill> get() {
			if (data == null) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderRecordItem item : data.list()) {
				OrderRecordFeeDetail feeDetail = null;
				if (item.feeDetail() != null) {
					feeDetail = item.feeDetail()
									.stream()
									.filter(fd -> "USDT".equalsIgnoreCase(fd.feeCoin))
									.findFirst()
									.orElse(null);
				}
				BigDecimal feeValue = feeDetail == null ? null : feeDetail.fee().abs();
				Instant ts = Instant.ofEpochMilli(item.createdTime());
				result.add(new PartialFill(item.orderId(), item.symbol(), item.execQty(), item.execPrice(), feeValue, ts));
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
				throw new IllegalStateException("Internal transfer failed: " + code + " " + msg);
			}
		}
	}
}
