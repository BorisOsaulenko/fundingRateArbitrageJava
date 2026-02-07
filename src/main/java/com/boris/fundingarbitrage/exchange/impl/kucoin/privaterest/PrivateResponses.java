package com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest;

import com.boris.fundingarbitrage.exchange.impl.kucoin.KucoinJson;
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
import java.util.List;

public class PrivateResponses {
	public record TradingFeesResponse(String code, String msg, JsonNode data) {
		public Fees getFees() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			double maker = KucoinJson.requireDouble(data, "makerFeeRate");
			double taker = KucoinJson.requireDouble(data, "takerFeeRate");
			Instant ts = Instant.now();
			return new Fees(maker, taker, maker, taker, ts);
		}
	}

	public record ChangeLeverageResponse(String code, String msg, JsonNode data) {
		public ChangeLeverageResponse {
			KucoinJson.requireCodeOk(code, msg);
		}
	}

	public record SetMarginModeResponse(String code, String msg, JsonNode data) {
		public SetMarginModeResponse {
			KucoinJson.requireCodeOk(code, msg);
		}
	}

	public record SpotUsdtBalanceResponse(String code, String msg, JsonNode data) {
		public double get() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("KuCoin spot balance data missing");
			}
			for (JsonNode account : data) {
				String currency = KucoinJson.requireText(account, "currency");
				String type = KucoinJson.requireText(account, "type");
				if (!"USDT".equalsIgnoreCase(currency)) continue;
				if (!"main".equalsIgnoreCase(type)) continue;
				return KucoinJson.requireDouble(account, "available");
			}
			throw new IllegalStateException("USDT main account not found in KuCoin spot balances");
		}
	}

	public record FuturesUsdtBalanceResponse(String code, String msg, JsonNode data) {
		public double get() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin futures balance data missing");
			}
			String currency = KucoinJson.requireText(data, "currency");
			if (!"USDT".equalsIgnoreCase(currency)) {
				throw new IllegalStateException("Unexpected futures currency: " + currency);
			}
			return KucoinJson.requireDouble(data, "availableBalance");
		}
	}

	public record MaxLeverageResponse(String code, String msg, JsonNode data) {
		public int get() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			String maxLeverageText = KucoinJson.requireText(data, "maxLeverage");
			try {
				return Integer.parseInt(maxLeverageText);
			} catch (NumberFormatException ex) {
				throw new IllegalStateException("Invalid maxLeverage", ex);
			}
		}
	}

	public record SupportedChainsResponse(String code, String msg, JsonNode data) {
		public ExchangeChains get() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin currency detail data missing");
			}
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			JsonNode chains = KucoinJson.requireArray(data, "chains");
			for (JsonNode chain : chains) {
				String chainName = KucoinJson.requireText(chain, "chainName");
				String chainId = KucoinJson.requireText(chain, "chainId");
				SupportedChain mapped = ChainsMap.getInverse(chainId);
				if (mapped == null) mapped = ChainsMap.getInverse(chainName);
				if (mapped == null) continue;

				boolean depositEnabled = KucoinJson.requireBoolean(chain, "isDepositEnabled");
				boolean withdrawEnabled = KucoinJson.requireBoolean(chain, "isWithdrawEnabled");
				if (depositEnabled) builder.addDepositableChain(mapped);
				if (withdrawEnabled) {
					double fee = KucoinJson.requireDouble(chain, "withdrawalMinFee");
					double min = KucoinJson.requireDouble(chain, "withdrawalMinSize");
					builder.addWithdrawableChain(new WithdrawChain(mapped, fee, min));
				}
			}
			return builder.build();
		}
	}

	public record UsdtWalletAddressResponse(String code, String msg, JsonNode data) {
		public WalletAddress get(SupportedChain chain) {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("KuCoin deposit address data missing");
			}
			String targetChainId = ChainsMap.get(chain);
			for (JsonNode entry : data) {
				String chainId = KucoinJson.requireText(entry, "chainId");
				if (!chainId.equalsIgnoreCase(targetChainId)) continue;
				String address = KucoinJson.requireText(entry, "address");
				String memo = entry.has("memo") && !entry.get("memo").isNull()
						? entry.get("memo").asText()
						: null;
				if (memo != null && memo.isEmpty()) memo = null;
				return new WalletAddress(chain, address, memo);
			}
			throw new IllegalStateException("USDT deposit address not found for chain: " + chain);
		}
	}

	public record WithdrawUsdtResponse(String code, String msg, JsonNode data) {
		public WithdrawUsdtResponse {
			KucoinJson.requireCodeOk(code, msg);
		}
	}

	public record PlaceFuturesOrderResponse(String code, String msg, JsonNode data) {
		public String orderId() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin order response data missing");
			}
			return KucoinJson.requireText(data, "orderId");
		}
	}

	public record GetOrderRecordResponse(String code, String msg, JsonNode data) {
		public List<PartialFill> get() {
			KucoinJson.requireCodeOk(code, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin fills data missing");
			}
			JsonNode items = KucoinJson.requireArray(data, "items");
			if (items.isEmpty()) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (JsonNode item : items) {
				String orderId = KucoinJson.requireText(item, "orderId");
				String symbol = KucoinJson.requireText(item, "symbol");
				double size = KucoinJson.requireDouble(item, "size");
				double price = KucoinJson.requireDouble(item, "price");
				double fee = KucoinJson.requireDouble(item, "fee");
				String feeCurrency = KucoinJson.requireText(item, "feeCurrency");
				long tradeTime = KucoinJson.requireLong(item, "tradeTime");
				Instant ts = KucoinJson.toInstantMillisOrNanos(tradeTime);
				Double feeValue = "USDT".equalsIgnoreCase(feeCurrency) ? fee : null;
				result.add(new PartialFill(orderId, symbol, size, price, feeValue, ts));
			}
			return result;
		}
	}

	public record InternalTransferResponse(String code, String msg, JsonNode data) {
		public InternalTransferResponse {
			KucoinJson.requireCodeOk(code, msg);
		}
	}
}
