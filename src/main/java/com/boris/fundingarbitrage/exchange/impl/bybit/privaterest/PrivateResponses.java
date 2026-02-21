package impl.bybit.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.boris.fundingarbitrage.util.https.PaginatedResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PrivateResponses {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TradingFeesResponse(int retCode, String retMsg, long time, TradingFeesResult result) {
		public Map<String, Fees> getFeesBySymbols() {
			Map<String, Fees> feesBySymbol = new HashMap<>();
			List<TradingFeeItem> list = result == null ? null : result.list;
			if (list == null) return feesBySymbol;
			for (TradingFeeItem item : list) {
				String symbol = item.symbol;
				double maker = Double.parseDouble(item.makerFeeRate);
				double taker = Double.parseDouble(item.takerFeeRate);
				feesBySymbol.put(symbol, new Fees(maker, taker, maker, taker, Instant.ofEpochMilli(time)));
			}
			return feesBySymbol;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TradingFeesResult(List<TradingFeeItem> list) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TradingFeeItem(String symbol, String makerFeeRate, String takerFeeRate) {}

	public record ChangeLeverageResponse(int retCode, String retMsg) {
		public ChangeLeverageResponse {
			if (retCode != 0 && retCode != 110043) {
				throw new RuntimeException(String.format("Failed to change leverage %d, %s", retCode, retMsg));
			}
		}
	}

	public record SetMarginModeResponse(int retCode, String retMsg) {
		public SetMarginModeResponse {}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SpotUsdtBalanceResponse(int retCode, String retMsg, long time, BalanceResult result) {
		public double get() {
			List<BalanceAccount> list = result == null ? null : result.list;
			if (list == null) return 0.0;
			for (BalanceAccount account : list) {
				if (account.coin == null) continue;
				for (BalanceCoin coin : account.coin) {
					if (!"USDT".equalsIgnoreCase(coin.coin)) continue;
					return Double.parseDouble(coin.walletBalance);
				}
			}
			return 0.0;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record FuturesUsdtBalanceResponse(int retCode, String retMsg, long time, BalanceResult result) {
		public double get() {
			List<BalanceAccount> list = result == null ? null : result.list;
			if (list == null) return 0.0;
			for (BalanceAccount account : list) {
				if (account.coin == null) continue;
				for (BalanceCoin coin : account.coin) {
					if (!"USDT".equalsIgnoreCase(coin.coin)) continue;
					return Double.parseDouble(coin.walletBalance);
				}
			}
			return 0.0;
		}
	}

	private record BalanceResult(List<BalanceAccount> list) {}

	private record BalanceAccount(List<BalanceCoin> coin) {}

	private record BalanceCoin(String coin, String walletBalance) {}

	public record MaxLeverageResponse(int retCode, String retMsg, long time, MaxLeverageResult result) implements
					PaginatedResponse {
		public Map<String, Integer> getMaxLeverages() {
			Map<String, Integer> maxLeverageBySymbol = new HashMap<>();

			for (MaxLeverageItem item : result.list) {
				String symbol = item.symbol;
				double maxLeverage = Double.parseDouble(item.leverageFilter.maxLeverage);
				if (maxLeverage == 0) throw new IllegalStateException("Invalid max leverage for symbol: " + symbol);
				maxLeverageBySymbol.put(symbol, (int) Math.floor(maxLeverage));
			}

			return maxLeverageBySymbol;
		}

		public String getPaginationIndex() {
			return result.nextPageCursor;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MaxLeverageResult(List<MaxLeverageItem> list, String nextPageCursor) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record MaxLeverageItem(String symbol, LeverageFilter leverageFilter) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record LeverageFilter(String maxLeverage) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SupportedChainsResponse(int retCode, String retMsg, long time, SupportedChainsResult result) {
		public ExchangeChains get() {
			if (result == null) throw new IllegalStateException("Supported chains info not found");
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			List<SupportedCoin> rows = result.rows;
			if (rows == null) {
				throw new IllegalStateException("Supported chains info not found");
			}

			for (SupportedCoin coin : rows) {
				if (!"USDT".equalsIgnoreCase(coin.coin)) continue;
				if (coin.chains == null) continue;
				for (SupportedChainInfo chain : coin.chains) {
					String chainName = chain.chain;
					SupportedChain mapped = ChainsMap.getInverse(chainName);
					if (mapped == null) continue;
					boolean depositEnable = "1".equals(chain.chainDeposit);
					boolean withdrawEnable = "1".equals(chain.chainWithdraw);
					if (depositEnable) builder.addDepositableChain(mapped);
					if (withdrawEnable) {
						double fee = Double.parseDouble(chain.withdrawFee);
						double min = Double.parseDouble(chain.withdrawMin);
						if (fee >= 0 && min >= 0) {
							builder.addWithdrawableChain(new WithdrawChain(mapped, fee, min));
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

	private record SupportedChainsResult(List<SupportedCoin> rows) {}

	private record SupportedCoin(String coin, List<SupportedChainInfo> chains) {}

	private record SupportedChainInfo(
					String chain, String chainDeposit, String chainWithdraw, String withdrawFee, String withdrawMin
	) {}

	private record WalletItem(String chain, String addressDeposit, String tagDeposit) {}

	private record WalletResult(String coin, List<WalletItem> chains) {}

	public record UsdtWalletAddressResponse(
					int retCode, String retMsg, long time, WalletResult result
	) {
		public WalletAddress get(SupportedChain chain) {
			if (result == null || result.chains == null) {
				throw new IllegalStateException("USDT wallet address response missing");
			}
			for (WalletItem item : result.chains) {
				String chainName = item.chain;
				SupportedChain mapped = ChainsMap.getInverse(chainName);
				if (mapped == chain) {
					String address = item.addressDeposit;
					String tag = item.tagDeposit;
					if (tag != null && tag.isEmpty()) tag = null;
					return new WalletAddress(chain, address, tag);
				}
			}
			throw new IllegalStateException("USDT wallet address not found for chain: " + chain);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record WithdrawResult(String id) {}

	public record WithdrawUsdtResponse(int retCode, String retMsg, long time, WithdrawResult result) {
		public WithdrawUsdtResponse {
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record PlaceFuturesOrderResult(String orderId, String orderLinkId) {}

	public record PlaceFuturesOrderResponse(int retCode, String retMsg, long time, PlaceFuturesOrderResult result) {
		public String orderId() {
			if (result == null) return null;
			String id = result.orderId;
			if (id == null || id.isEmpty()) id = result.orderLinkId;
			return id;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OrderRecordsResult(List<OrderRecordItem> list) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OrderRecordItem(
					String orderId,
					String symbol,
					String execQuantity,
					String execPrice,
					String execFee,
					String feeCurrency,
					long execTime
	) {}

	public record GetOrderRecordResponse(int retCode, String retMsg, long time, OrderRecordsResult result) {
		public List<PartialFill> get() {
			List<OrderRecordItem> list = result == null ? null : result.list;
			if (list == null) return List.of();
			ArrayList<PartialFill> fills = new ArrayList<>();
			for (OrderRecordItem item : list) {
				String orderId = item.orderId;
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.symbol;
				double qty = Double.parseDouble(item.execQuantity);
				double price = Double.parseDouble(item.execPrice);
				double fee = Double.parseDouble(item.execFee);
				String feeCoin = item.feeCurrency;
				Double feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
				Instant ts = Instant.ofEpochMilli(item.execTime);
				fills.add(new PartialFill(orderId, symbol, qty, price, feeValue, ts));
			}
			return fills;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record InternalTransferResult(String transferId, String status) {}

	public record InternalTransferResponse(int retCode, String retMsg, long time, InternalTransferResult result) {
		public InternalTransferResponse {
		}
	}
}
