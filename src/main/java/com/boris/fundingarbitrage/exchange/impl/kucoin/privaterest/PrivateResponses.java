package com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrivateResponses {
	private static final KucoinChainsMap chainsMap = new KucoinChainsMap();
	private static final String expectedSuccessCode = "200000";

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TradingFeeData(String makerFeeRate, String takerFeeRate) {
	}

	public record TradingFeesResponse(String code, String msg, TradingFeeData data) {
		public Fees getFees() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin contract detail response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			if (data.makerFeeRate == null || data.makerFeeRate.isEmpty()) {
				throw new IllegalStateException("KuCoin makerFeeRate missing");
			}
			if (data.takerFeeRate == null || data.takerFeeRate.isEmpty()) {
				throw new IllegalStateException("KuCoin takerFeeRate missing");
			}
			BigDecimal maker = new BigDecimal(data.makerFeeRate);
			BigDecimal taker = new BigDecimal(data.takerFeeRate);
			Instant ts = Instant.now();
			return new Fees(maker, taker, maker, taker, ts);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TradingFeeSymbolData(String symbol, String makerFeeRate, String takerFeeRate) {
	}

	public record TradingFeesSymbolsResponse(String code, String msg, List<TradingFeeSymbolData> data) {
		public Map<String, Fees> getFeesBySymbols() {
			Map<String, Fees> feesBySymbol = new HashMap<>();
			if (data == null) return feesBySymbol;
			for (TradingFeeSymbolData contract : data) {
				if (contract.makerFeeRate == null || contract.makerFeeRate.isEmpty()) continue;
				if (contract.takerFeeRate == null || contract.takerFeeRate.isEmpty()) continue;

				BigDecimal maker = new BigDecimal(contract.makerFeeRate);
				BigDecimal taker = new BigDecimal(contract.takerFeeRate);
				feesBySymbol.put(contract.symbol, new Fees(maker, taker, maker, taker, Instant.now()));
			}
			return feesBySymbol;
		}
	}

	private record OperationData(String orderId) {
	}

	public record ChangeLeverageResponse(String code, boolean data) {
		public ChangeLeverageResponse {
			if (!expectedSuccessCode.equals(code) || !data) {
				throw new IllegalStateException("KuCoin change leverage response code not OK: " + code);
			}
		}
	}

	public record SetMarginModeResponse(String code, String msg, OperationData data) {
		public SetMarginModeResponse {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin set margin mode response code not OK: " + code + ", msg: " + msg);
			}
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SpotBalanceAccount(String currency, String type, String available) {
	}

	public record SpotUsdtBalanceResponse(String code, String msg, List<SpotBalanceAccount> data) {
		public BigDecimal get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin spot balance response code not OK: " + code + ", msg: " + msg);
			}

			for (SpotBalanceAccount account : data) {
				if (!"USDT".equalsIgnoreCase(account.currency)) continue;
				if (!"main".equalsIgnoreCase(account.type)) continue;
				BigDecimal available = new BigDecimal(account.available);
				if (available.compareTo(BigDecimal.ZERO) == 0) {
					throw new IllegalStateException("USDT main account available balance missing in KuCoin spot balances");
				}

				return available;
			}
			throw new IllegalStateException("USDT main account not found in KuCoin spot balances");
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record FuturesBalanceData(String currency, String availableBalance) {
	}

	public record FuturesUsdtBalanceResponse(String code, String msg, FuturesBalanceData data) {
		public BigDecimal get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin futures balance response code not OK: " + code + ", msg: " + msg);
			}

			String currency = data.currency;
			if (!"USDT".equalsIgnoreCase(currency)) {
				throw new IllegalStateException("Unexpected futures currency: " + currency);
			}

			BigDecimal available = new BigDecimal(data.availableBalance);
			if (available.compareTo(BigDecimal.ZERO) == 0) {
				throw new IllegalStateException("KuCoin futures availableBalance missing");
			}

			return available;
		}
	}

	private record MaxLeverageItem(String symbol, BigDecimal maxLeverage) {
	}

	public record MaxLeverageResponse(String code, String msg, List<MaxLeverageItem> data) {
		public Map<String, Integer> get() {
			Map<String, Integer> result = new HashMap<>();
			for (MaxLeverageItem item : data) result.put(item.symbol(), item.maxLeverage().intValue());
			return result;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CurrencyChainData(
					String chainId,
					boolean isDepositEnabled,
					boolean isWithdrawEnabled,
					String withdrawalMinFee,
					String withdrawalMinSize
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CurrencyDetailData(List<CurrencyChainData> chains) {
	}

	public record SupportedChainsResponse(String code, String msg, CurrencyDetailData data) {
		public ExchangeChains get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin currency detail response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null) {
				throw new IllegalStateException("KuCoin currency detail data missing");
			}
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			List<CurrencyChainData> chains = data.chains;
			if (chains == null) {
				throw new IllegalStateException("KuCoin currency chains missing");
			}
			for (CurrencyChainData chain : chains) {
				String chainId = chain.chainId;
				if (chainId == null || chainId.isEmpty()) continue;
				SupportedChain mapped = chainsMap.getInverse(chainId);
				if (mapped == null) continue;

				if (chain.isDepositEnabled) builder.addDepositableChain(mapped);
				if (chain.isWithdrawEnabled && chain.withdrawalMinFee != null && chain.withdrawalMinSize != null) {
					BigDecimal fee = new BigDecimal(chain.withdrawalMinFee);
					BigDecimal min = new BigDecimal(chain.withdrawalMinSize);
					builder.addWithdrawableChain(new WithdrawChain(mapped, fee, min));
				}
			}
			return builder.build();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record DepositAddressData(String chainId, String address, String memo) {
	}

	public record UsdtWalletAddressResponse(String code, String msg, List<DepositAddressData> data) {
		public WalletAddress get(SupportedChain chain) {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null) {
				throw new IllegalStateException("KuCoin deposit address data missing");
			}

			String targetChainId = chainsMap.get(chain);
			for (DepositAddressData entry : data) {
				String chainId = entry.chainId;
				if (chainId == null || chainId.isEmpty() || !chainId.equalsIgnoreCase(targetChainId)) continue;

				String address = entry.address;
				if (address == null || address.isEmpty()) {
					throw new IllegalStateException("KuCoin deposit address missing for chain: " + chain);
				}

				String memo = entry.memo;
				return new WalletAddress(chain, address, memo);
			}
			throw new IllegalStateException("USDT deposit address not found for chain: " + chain);
		}
	}

	public record WithdrawUsdtResponse(String code, String msg, OperationData data) {
		public WithdrawUsdtResponse {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}
		}
	}

	public record PlaceFuturesOrderResponse(String code, String msg, OperationData data) {
		public String orderId() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null) {
				throw new IllegalStateException("KuCoin order response data missing");
			}

			String orderId = data.orderId;
			if (orderId == null || orderId.isEmpty()) {
				throw new IllegalStateException("KuCoin orderId missing in response");
			}

			return orderId;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record FillItem(
					String orderId, String symbol, String size, String price, String fee, long createdAt, String feeCurrency
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record FillsData(List<FillItem> items) {
	}

	public record GetOrderRecordResponse(String code, String msg, FillsData data) {
		public List<PartialFill> get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null) {
				throw new IllegalStateException("KuCoin fills data missing");
			}

			List<FillItem> items = data.items;
			if (items == null) {
				throw new IllegalStateException("KuCoin fills items missing");
			}

			if (items.isEmpty()) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (FillItem item : items) {
				String orderId = item.orderId;
				if (orderId == null || orderId.isEmpty()) continue;

				String symbol = item.symbol;
				if (symbol == null || symbol.isEmpty()) continue;

				BigDecimal size = new BigDecimal(item.size);
				if (size.compareTo(BigDecimal.ZERO) == 0) continue;

				BigDecimal price = new BigDecimal(item.price);
				if (price.compareTo(BigDecimal.ZERO) == 0) continue;

				BigDecimal fee = new BigDecimal(item.fee);
				if (fee.compareTo(BigDecimal.ZERO) == 0) continue;

				long tradeTime = item.createdAt;
				if (tradeTime == 0L) continue;

				Instant ts = Instant.ofEpochMilli(tradeTime);
				String feeCurrency = item.feeCurrency;
				BigDecimal feeValue = "USDT".equalsIgnoreCase(feeCurrency) ? fee : null;
				result.add(new PartialFill(orderId, symbol, size, price, feeValue, ts));
			}
			return result;
		}
	}

	public record InternalTransferResponse(String code, String msg, OperationData data) {
		public InternalTransferResponse {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}
		}
	}
}
