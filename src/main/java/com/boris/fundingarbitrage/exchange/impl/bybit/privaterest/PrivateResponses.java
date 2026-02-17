package com.boris.fundingarbitrage.exchange.impl.bybit.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrivateResponses {
	public record TradingFeesResponse(int retCode, String retMsg, long time, JsonNode result) {
		public Map<String, Fees> getFeesBySymbols() {
			Map<String, Fees> feesBySymbol = new HashMap<>();
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return feesBySymbol;
			for (JsonNode item : list) {
				String symbol = item.path("symbol").asText();
				double maker = Double.parseDouble(item.path("makerFeeRate").asText());
				double taker = Double.parseDouble(item.path("takerFeeRate").asText());
				feesBySymbol.put(symbol, new Fees(maker, taker, maker, taker, Instant.ofEpochMilli(time)));
			}
			return feesBySymbol;
		}
	}

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

	public record SpotUsdtBalanceResponse(int retCode, String retMsg, long time, JsonNode result) {
		public double get() {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return 0.0;
			for (JsonNode account : list) {
				JsonNode coinList = account.get("coin");
				if (coinList == null || !coinList.isArray()) continue;
				for (JsonNode coin : coinList) {
					if (!"USDT".equalsIgnoreCase(coin.path("coin").asText())) continue;
					return Double.parseDouble(coin.path("walletBalance").asText());
				}
			}
			return 0.0;
		}
	}

	public record FuturesUsdtBalanceResponse(int retCode, String retMsg, long time, JsonNode result) {
		public double get() {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return 0.0;
			for (JsonNode account : list) {
				JsonNode coinList = account.get("coin");
				if (coinList == null || !coinList.isArray()) continue;
				for (JsonNode coin : coinList) {
					if (!"USDT".equalsIgnoreCase(coin.path("coin").asText())) continue;
					return Double.parseDouble(coin.path("walletBalance").asText());
				}
			}
			return 0.0;
		}
	}

	public record MaxLeverageResponse(int retCode, String retMsg, long time, JsonNode result) {
		public Map<String, Integer> get() {
			JsonNode list = result.get("list");
			Map<String, Integer> result = new HashMap<>();

			for (JsonNode item : list) {
				String symbol = item.path("symbol").asText();
				double maxLeverage = item.path("leverageFilter").path("maxLeverage").asDouble();
				if (maxLeverage == 0) throw new IllegalStateException("Invalid max leverage for symbol: " + symbol);
				result.put(symbol, (int) Math.floor(maxLeverage));
			}

			return result;
		}
	}

	public record SupportedChainsResponse(int retCode, String retMsg, long time, JsonNode result) {
		public ExchangeChains get() {
			if (result == null) throw new IllegalStateException("Supported chains info not found");
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			JsonNode rows = result.get("rows");
			if (rows == null || !rows.isArray()) {
				throw new IllegalStateException("Supported chains info not found");
			}

			for (JsonNode coin : rows) {
				if (!"USDT".equalsIgnoreCase(coin.path("coin").asText())) continue;
				JsonNode chains = coin.get("chains");
				if (chains == null || !chains.isArray()) continue;
				for (JsonNode chain : chains) {
					String chainName = chain.path("chain").asText();
					SupportedChain mapped = ChainsMap.getInverse(chainName);
					if (mapped == null) continue;
					boolean depositEnable = "1".equals(chain.path("chainDeposit").asText());
					boolean withdrawEnable = "1".equals(chain.path("chainWithdraw").asText());
					if (depositEnable) builder.addDepositableChain(mapped);
					if (withdrawEnable) {
						double fee = Double.parseDouble(chain.path("withdrawFee").asText());
						double min = Double.parseDouble(chain.path("withdrawMin").asText());
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

	private record WalletItem(String chain, String addressDeposit, String tagDeposit) {}

	private record WalletResult(String coin, WalletItem[] chains) {}

	public record UsdtWalletAddressResponse(
					int retCode, String retMsg, long time, WalletResult result
	) {
		public WalletAddress get(SupportedChain chain) {
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

	public record WithdrawUsdtResponse(int retCode, String retMsg, long time, JsonNode result) {
		public WithdrawUsdtResponse {
		}
	}

	public record PlaceFuturesOrderResponse(int retCode, String retMsg, long time, JsonNode result) {
		public String orderId() {
			String id = result.path("orderId").asText();
			if (id == null || id.isEmpty()) id = result.path("orderLinkId").asText();
			return id;
		}
	}

	public record GetOrderRecordResponse(int retCode, String retMsg, long time, JsonNode result) {
		public List<PartialFill> get() {
			JsonNode list = result == null ? null : result.get("list");
			if (list == null || !list.isArray()) return List.of();
			ArrayList<PartialFill> fills = new ArrayList<>();
			for (JsonNode item : list) {
				String orderId = item.path("orderId").asText();
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.path("symbol").asText();
				double qty = Double.parseDouble(item.path("execQuantity").asText());
				double price = Double.parseDouble(item.path("execPrice").asText());
				double fee = Double.parseDouble(item.path("execFee").asText());
				String feeCoin = item.path("feeCurrency").asText();
				Double feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
				Instant ts = Instant.ofEpochMilli(item.path("execTime").asLong());
				fills.add(new PartialFill(orderId, symbol, qty, price, feeValue, ts));
			}
			return fills;
		}
	}

	public record InternalTransferResponse(int retCode, String retMsg, long time, JsonNode result) {
		public InternalTransferResponse {
		}
	}
}
