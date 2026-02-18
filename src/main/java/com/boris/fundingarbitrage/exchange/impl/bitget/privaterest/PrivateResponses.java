package com.boris.fundingarbitrage.exchange.impl.bitget.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	private static double parseDouble(String... values) {
		for (String text : values) {
			if (text == null || text.isEmpty()) continue;
			try {
				return Double.parseDouble(text);
			} catch (NumberFormatException ignored) {
			}
		}
		return 0.0;
	}

	public record ChangeLeverageResponse(String code, String msg, long requestTime) {
		public ChangeLeverageResponse {
			if (!"00000".equals(code)) {
				throw new IllegalStateException("Change leverage failed: " + code + " " + msg);
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SpotAsset(
					String coin, String available, String free, String availableBalance
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SpotAccount(
					String accountType, String available, String availableBalance, String availableEquity, List<SpotAsset> assets
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SpotUsdtBalanceResponse(String code, String msg, long requestTime, List<SpotAccount> data) {
		public double get() {
			if (data == null) return 0.0;
			for (SpotAccount account : data) {
				String accountType = account.accountType == null ? "" : account.accountType;
				if (!accountType.toLowerCase().contains("spot")) continue;
				double direct = parseDouble(account.available, account.availableBalance, account.availableEquity);
				if (direct > 0) return direct;

				if (account.assets != null) {
					for (SpotAsset asset : account.assets) {
						if (!"USDT".equalsIgnoreCase(asset.coin)) continue;
						double available = parseDouble(asset.available, asset.free, asset.availableBalance);
						if (available > 0) return available;
					}
				}
			}
			return 0.0;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record FuturesAccount(
					String marginCoin, String available, String availableBalance, String availableEquity, String maxAvailable
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record FuturesUsdtBalanceResponse(
					String code, String msg, long requestTime, List<FuturesAccount> data
	) {
		public double get() {
			if (data == null) return 0.0;
			for (FuturesAccount account : data) {
				String marginCoin = account.marginCoin;
				if (!"USDT".equalsIgnoreCase(marginCoin)) continue;
				return parseDouble(account.available, account.availableBalance, account.availableEquity, account.maxAvailable);
			}
			return 0.0;
		}
	}

	private record ContractsItem(String symbol, int maxLever, double makerFeeRate, double takerFeeRate) {}

	public record ContractsResponse(String code, String msg, long requestTime, List<ContractsItem> data) {
		public Map<String, Integer> getMaxLeverages() {
			Map<String, Integer> maxLeverageBySymbol = new HashMap<>();
			if (data == null) return maxLeverageBySymbol;
			for (ContractsItem item : data) maxLeverageBySymbol.put(item.symbol, item.maxLever);
			return maxLeverageBySymbol;
		}

		public Map<String, Fees> getFees() {
			Map<String, Fees> feesBySymbol = new HashMap<>();
			if (data == null) return feesBySymbol;
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ChainInfo(
					String chain,
					String network,
					Boolean rechargeable,
					Boolean depositEnable,
					Boolean withdrawable,
					Boolean withdrawEnable,
					String withdrawFee,
					String withdrawFeeRate,
					String minWithdrawAmount,
					String withdrawMin,
					String minWithdraw
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CoinInfo(
					String coin, List<ChainInfo> chains, List<ChainInfo> chainList
	) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SupportedChainsResponse(String code, String msg, long requestTime, List<CoinInfo> data) {
		public ExchangeChains get() {
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			if (data == null) return builder.build();
			for (CoinInfo coin : data) {
				String coinName = coin.coin;
				if (!"USDT".equalsIgnoreCase(coinName)) continue;

				List<ChainInfo> chains = coin.chains;
				if (chains == null) chains = coin.chainList;
				if (chains == null) continue;

				for (ChainInfo chain : chains) {
					String chainName = chain.chain;
					if (chainName == null || chainName.isEmpty()) chainName = chain.network;
					SupportedChain mapped = ChainsMap.getInverse(chainName);
					if (mapped == null) continue;

					boolean depositEnable = Boolean.TRUE.equals(chain.rechargeable) || Boolean.TRUE.equals(chain.depositEnable);
					boolean withdrawEnable = Boolean.TRUE.equals(chain.withdrawable) || Boolean.TRUE.equals(chain.withdrawEnable);

					if (depositEnable) builder.addDepositableChain(mapped);
					if (withdrawEnable) {
						double fee = parseDouble(chain.withdrawFee, chain.withdrawFeeRate);
						double min = parseDouble(chain.minWithdrawAmount, chain.withdrawMin, chain.minWithdraw);
						if (fee > 0 && min > 0) {
							builder.addWithdrawableChain(new WithdrawChain(mapped, fee, min));
						}
					}
				}
				return builder.build();
			}
			return builder.build();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WalletAddressData(String address, String tag) {}

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

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WithdrawUsdtData(String orderId, String clientOid) {}

	public record WithdrawUsdtResponse(String code, String msg, long requestTime, WithdrawUsdtData data) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record PlaceOrderData(String orderId, String orderIdStr) {}

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

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OrderRecordItem(
					String orderId,
					String symbol,
					String size,
					String baseVolume,
					String tradeSize,
					String price,
					String tradePrice,
					String fee,
					String feeCoin,
					long cTime
	) {}

	public record GetOrderRecordResponse(String code, String msg, long requestTime, List<OrderRecordItem> data) {
		public List<PartialFill> get() {
			if (data == null) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (OrderRecordItem item : data) {
				String orderId = item.orderId;
				String symbol = item.symbol;
				double size = parseDouble(item.size, item.baseVolume, item.tradeSize);
				double price = parseDouble(item.price, item.tradePrice);
				double fee = parseDouble(item.fee);
				String feeCoin = item.feeCoin;
				Double feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
				long cTime = item.cTime;
				if (cTime == 0L) throw new IllegalStateException("Invalid timestamp for order: " + orderId);
				Instant ts = Instant.ofEpochMilli(cTime);
				if (orderId == null || orderId.isEmpty()) continue;
				result.add(new PartialFill(orderId, symbol, size, price, feeValue, ts));
			}
			return result;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record InternalTransferData(String transferId, String status) {}

	public record InternalTransferResponse(
					String code, String msg, long requestTime, InternalTransferData data
	) {}
}
