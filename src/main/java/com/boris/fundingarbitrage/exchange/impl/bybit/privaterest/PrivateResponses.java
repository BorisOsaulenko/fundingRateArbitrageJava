package com.boris.fundingarbitrage.exchange.impl.bybit.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.boris.fundingarbitrage.util.https.PaginatedResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	private static final BybitChainsMap chainsMap = new BybitChainsMap();

	record FuturesTradingFeesResponse(int retCode, String retMsg, long time, TradingFeesResult result) {
		Map<String, Fees> getFeesBySymbols() {
			Map<String, Fees> feesBySymbol = new HashMap<>();
			List<TradingFeeItem> list = result == null ? null : result.list;
			if (list == null) return feesBySymbol;
			for (TradingFeeItem item : list) {
				String symbol = item.symbol;
				BigDecimal maker = new BigDecimal(item.makerFeeRate);
				BigDecimal taker = new BigDecimal(item.takerFeeRate);
				feesBySymbol.put(symbol, new Fees(maker, taker, maker, taker, Instant.ofEpochMilli(time)));
			}
			return feesBySymbol;
		}
	}

	record SpotTradingFeesResponse(int retCode, String retMsg, long time, TradingFeesResult result) {
		Map<String, Fees> getFeesSymbolMap() {
			if (retCode != 0) throw new RuntimeException("Failed to get spot trading fees");
			Map<String, Fees> feesBySymbol = new HashMap<>();
			for (TradingFeeItem item : result.list) {
				String symbol = item.symbol;
				BigDecimal maker = new BigDecimal(item.makerFeeRate);
				BigDecimal taker = new BigDecimal(item.takerFeeRate);
				feesBySymbol.put(symbol, new Fees(maker, taker, maker, taker, Instant.ofEpochMilli(time)));
			}

			return feesBySymbol;
		}
	}

	private record TradingFeesResult(List<TradingFeeItem> list) {
	}

	private record TradingFeeItem(String symbol, String makerFeeRate, String takerFeeRate) {
	}

	public record ChangeLeverageResponse(int retCode, String retMsg) {
		public ChangeLeverageResponse {
			if (retCode != 0 && retCode != 110043) {
				throw new RuntimeException(String.format("Failed to change leverage %d, %s", retCode, retMsg));
			}
		}
	}

	public record SetMarginModeResponse(int retCode, String retMsg) {
	}

	public record SpotUsdtBalanceResponse(int retCode, String retMsg, long time, SpotBalanceResult result) {
		public BigDecimal get() {
			for (BalanceItem item : result.balance()) {
				if (!"USDT".equalsIgnoreCase(item.coin())) continue;
				return item.walletBalance();
			}
			throw new IllegalStateException("Bybit spot USDT balance not found");
		}

		private record BalanceItem(String coin, BigDecimal walletBalance) {
		}

		private record SpotBalanceResult(List<BalanceItem> balance) {
		}
	}

	public record FuturesUsdtBalanceResponse(int retCode, String retMsg, long time, FuturesBalanceResult result) {
		public BigDecimal get() {
			for (BalanceAccount account : result.list) {
				if (account.coin == null) continue;
				for (BalanceCoin coin : account.coin) {
					if (!"USDT".equalsIgnoreCase(coin.coin)) continue;
					return coin.walletBalance;
				}
			}
			throw new IllegalStateException("Bybit futures USDT balance not found");
		}

		private record FuturesBalanceResult(List<BalanceAccount> list) {
		}

		private record BalanceAccount(List<BalanceCoin> coin) {
		}

		private record BalanceCoin(String coin, BigDecimal walletBalance) {
		}
	}


	public record MaxLeverageResponse(int retCode, String retMsg, long time, MaxLeverageResult result) implements
					PaginatedResponse {
		public Map<String, Integer> getMaxLeverages() {
			Map<String, Integer> maxLeverageBySymbol = new HashMap<>();

			for (MaxLeverageItem item : result.list) {
				String symbol = item.symbol;
				BigDecimal maxLeverage = new BigDecimal(item.leverageFilter.maxLeverage);
				if (maxLeverage.compareTo(BigDecimal.ZERO) == 0)
					throw new IllegalStateException("Invalid max leverage for symbol: " + symbol);
				maxLeverageBySymbol.put(symbol, maxLeverage.intValue());
			}

			return maxLeverageBySymbol;
		}

		public String getPaginationIndex() {
			return result.nextPageCursor;
		}
	}

	private record MaxLeverageResult(List<MaxLeverageItem> list, String nextPageCursor) {
	}

	private record MaxLeverageItem(String symbol, LeverageFilter leverageFilter) {
	}

	private record LeverageFilter(String maxLeverage) {
	}

	public record SupportedChainsResponse(int retCode, String retMsg, long time, SupportedChainsResult result) {
		public ExchangeChains getSupportedChains() {
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			List<SupportedCoin> rows = result.rows;

			for (SupportedCoin coin : rows) {
				if (!"USDT".equalsIgnoreCase(coin.coin)) continue;
				if (coin.chains == null) continue;
				for (SupportedChainInfo chain : coin.chains) {
					String chainName = chain.chain;
					SupportedChain mapped = chainsMap.getInverse(chainName);
					if (mapped == null) continue;
					boolean depositEnable = "1".equals(chain.chainDeposit);
					boolean withdrawEnable = "1".equals(chain.chainWithdraw);
					if (depositEnable) builder.addDepositableChain(mapped);
					if (withdrawEnable) {
						BigDecimal fee = new BigDecimal(chain.withdrawFee);
						BigDecimal min = new BigDecimal(chain.withdrawMin);
						if (fee.compareTo(BigDecimal.ZERO) >= 0 && min.compareTo(BigDecimal.ZERO) >= 0) {
							builder.addWithdrawableChain(new WithdrawChain(mapped, fee, min, chain.precisionPoints()));
						} else {
							throw new IllegalStateException("Invalid withdraw fee/min for chain: " + chainName);
						}
					}
				}
				return builder.build();
			}
			throw new IllegalStateException("USDT info not found in supported chains response");
		}
	}

	private record SupportedChainsResult(List<SupportedCoin> rows) {
	}

	private record SupportedCoin(String coin, List<SupportedChainInfo> chains) {
	}

	private record SupportedChainInfo(
					String chain,
					String chainDeposit,
					String chainWithdraw,
					String withdrawFee,
					String withdrawMin,
					String minAccuracy
	) {
		int precisionPoints() {
			if (minAccuracy == null || minAccuracy.isEmpty()) {
				throw new IllegalStateException("Bybit minAccuracy missing for chain: " + chain);
			}
			int precision = Integer.parseInt(minAccuracy);
			if (precision <= 0) {
				throw new IllegalStateException("Bybit minAccuracy must be positive for chain: " + chain);
			}
			return precision;
		}
	}

	private record WalletItem(String chain, String addressDeposit, String tagDeposit) {
	}

	private record WalletResult(String coin, List<WalletItem> chains) {
	}

	public record UsdtWalletAddressResponse(
					int retCode, String retMsg, long time, WalletResult result
	) {
		public WalletAddress get(SupportedChain chain) {
			for (WalletItem item : result.chains) {
				String chainName = item.chain;
				SupportedChain mapped = chainsMap.getInverse(chainName);
				if (!chain.equals(mapped)) continue;

				String address = item.addressDeposit;
				String tag = item.tagDeposit;
				if (tag != null && tag.isEmpty()) tag = null;
				return new WalletAddress(chain, address, tag);
			}
			throw new IllegalStateException("USDT wallet address not found for chain: " + chain);
		}
	}

	private record WithdrawResult(String id) {
	}

	public record WithdrawUsdtResponse(int retCode, String retMsg, long time, WithdrawResult result) {
	}

	private record PlaceFuturesOrderResult(String orderId, String orderLinkId) {
	}

	public record PlaceFuturesOrderResponse(int retCode, String retMsg, long time, PlaceFuturesOrderResult result) {
		public String orderId() {
			if (result == null || result.orderId == null || result.orderId.isEmpty())
				throw new RuntimeException("Order id not found");
			return result.orderId;
		}
	}

	private record PlaceSpotOrderResult(String orderId, String orderLinkId) {
	}

	public record PlaceSpotOrderResponse(int retCode, String retMsg, long time, PlaceSpotOrderResult result) {
		public String orderId() {
			if (result == null || result.orderId == null || result.orderId.isEmpty())
				throw new RuntimeException("Order id not found");
			return result.orderId;
		}
	}

	private record OrderRecordsResult(List<OrderRecordItem> list) {
	}

	private record OrderRecordItem(
					String orderId,
					String symbol,
					String execQty,
					String execPrice,
					String execFee,
					String feeCurrency,
					long execTime
	) {
	}

	public record GetOrderRecordResponse(int retCode, String retMsg, long time, OrderRecordsResult result) {
		public List<PartialFill> get() {
			List<OrderRecordItem> list = result == null ? null : result.list;
			if (list == null) return List.of();
			ArrayList<PartialFill> fills = new ArrayList<>();
			for (OrderRecordItem item : list) {
				String orderId = item.orderId;
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.symbol;
				BigDecimal qty = new BigDecimal(item.execQty);
				BigDecimal price = new BigDecimal(item.execPrice);
				BigDecimal fee = new BigDecimal(item.execFee);
				String feeCoin = item.feeCurrency;
				BigDecimal feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
				Instant ts = Instant.ofEpochMilli(item.execTime);
				fills.add(new PartialFill(orderId, symbol, qty, price, feeValue, ts));
			}
			return fills;
		}
	}

	private record InternalTransferResult(String transferId, String status) {
	}

	public record InternalTransferResponse(int retCode, String retMsg, long time, InternalTransferResult result) {
	}
}
