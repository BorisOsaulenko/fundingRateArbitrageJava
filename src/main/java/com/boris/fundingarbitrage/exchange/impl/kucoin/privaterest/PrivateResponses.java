package com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.ExchangeChainsBuilder;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.model.exchange.WithdrawChain;
import com.boris.fundingarbitrage.util.json.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PrivateResponses {
	private static final String expectedSuccessCode = "200000";

	public record TradingFeesResponse(String code, String msg, JsonNode data) {
		public Fees getFees() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			double maker = Json.requireDouble(data, "makerFeeRate");
			double taker = Json.requireDouble(data, "takerFeeRate");
			Instant ts = Instant.now();
			return new Fees(maker, taker, maker, taker, ts);
		}
	}

	public record ChangeLeverageResponse(String code, String msg, JsonNode data) {
		public ChangeLeverageResponse {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
		}
	}

	public record SetMarginModeResponse(String code, String msg, JsonNode data) {
		public SetMarginModeResponse {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
		}
	}

	public record SpotUsdtBalanceResponse(String code, String msg, JsonNode data) {
		public double get() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("KuCoin spot balance data missing");
			}
			for (JsonNode account : data) {
				String currency = Json.requireText(account, "currency");
				String type = Json.requireText(account, "type");
				if (!"USDT".equalsIgnoreCase(currency)) continue;
				if (!"main".equalsIgnoreCase(type)) continue;
				return Json.requireDouble(account, "available");
			}
			throw new IllegalStateException("USDT main account not found in KuCoin spot balances");
		}
	}

	public record FuturesUsdtBalanceResponse(String code, String msg, JsonNode data) {
		public double get() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin futures balance data missing");
			}
			String currency = Json.requireText(data, "currency");
			if (!"USDT".equalsIgnoreCase(currency)) {
				throw new IllegalStateException("Unexpected futures currency: " + currency);
			}
			return Json.requireDouble(data, "availableBalance");
		}
	}

	public record MaxLeverageResponse(String code, String msg, JsonNode data) {
		public int get() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			String maxLeverageText = Json.requireText(data, "maxLeverage");
			try {
				return Integer.parseInt(maxLeverageText);
			} catch (NumberFormatException ex) {
				throw new IllegalStateException("Invalid maxLeverage", ex);
			}
		}
	}

	public record SupportedChainsResponse(String code, String msg, JsonNode data) {
		public ExchangeChains get() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin currency detail data missing");
			}
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			JsonNode chains = Json.requireArray(data, "chains");
			for (JsonNode chain : chains) {
				String chainName = Json.requireText(chain, "chainName");
				String chainId = Json.requireText(chain, "chainId");
				SupportedChain mapped = ChainsMap.getInverse(chainId);
				if (mapped == null) mapped = ChainsMap.getInverse(chainName);
				if (mapped == null) continue;

				boolean depositEnabled = Json.requireBoolean(chain, "isDepositEnabled");
				boolean withdrawEnabled = Json.requireBoolean(chain, "isWithdrawEnabled");
				if (depositEnabled) builder.addDepositableChain(mapped);
				if (withdrawEnabled) {
					double fee = Json.requireDouble(chain, "withdrawalMinFee");
					double min = Json.requireDouble(chain, "withdrawalMinSize");
					builder.addWithdrawableChain(new WithdrawChain(mapped, fee, min));
				}
			}
			return builder.build();
		}
	}

	public record UsdtWalletAddressResponse(String code, String msg, JsonNode data) {
		public WalletAddress get(SupportedChain chain) {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("KuCoin deposit address data missing");
			}
			String targetChainId = ChainsMap.get(chain);
			for (JsonNode entry : data) {
				String chainId = Json.requireText(entry, "chainId");
				if (!chainId.equalsIgnoreCase(targetChainId)) continue;
				String address = Json.requireText(entry, "address");
				String memo = entry.has("memo") && !entry.get("memo").isNull() ? entry.get("memo").asText() : null;
				if (memo != null && memo.isEmpty()) memo = null;
				return new WalletAddress(chain, address, memo);
			}
			throw new IllegalStateException("USDT deposit address not found for chain: " + chain);
		}
	}

	public record WithdrawUsdtResponse(String code, String msg, JsonNode data) {
		public WithdrawUsdtResponse {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
		}
	}

	public record PlaceFuturesOrderResponse(String code, String msg, JsonNode data) {
		public String orderId() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin order response data missing");
			}
			return Json.requireText(data, "orderId");
		}
	}

	public record GetOrderRecordResponse(String code, String msg, JsonNode data) {
		public List<PartialFill> get() {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin fills data missing");
			}
			JsonNode items = Json.requireArray(data, "items");
			if (items.isEmpty()) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (JsonNode item : items) {
				String orderId = Json.requireText(item, "orderId");
				String symbol = Json.requireText(item, "symbol");
				double size = Json.requireDouble(item, "size");
				double price = Json.requireDouble(item, "price");
				double fee = Json.requireDouble(item, "fee");
				String feeCurrency = Json.requireText(item, "feeCurrency");
				long tradeTime = Json.requireLong(item, "tradeTime");
				Instant ts = Json.toInstantMillisOrNanos(tradeTime);
				Double feeValue = "USDT".equalsIgnoreCase(feeCurrency) ? fee : null;
				result.add(new PartialFill(orderId, symbol, size, price, feeValue, ts));
			}
			return result;
		}
	}

	public record InternalTransferResponse(String code, String msg, JsonNode data) {
		public InternalTransferResponse {
			Json.requireCodeOk(code, expectedSuccessCode, msg);
		}
	}
}
