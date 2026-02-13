package com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest;

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
	private static final String expectedSuccessCode = "200000";

	public record TradingFeesResponse(String code, String msg, JsonNode data) {
		public Fees getFees() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin contract detail response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			JsonNode makerNode = data.get("makerFeeRate");
			JsonNode takerNode = data.get("takerFeeRate");
			if (makerNode == null || makerNode.isNull()) {
				throw new IllegalStateException("KuCoin makerFeeRate missing");
			}
			if (takerNode == null || takerNode.isNull()) {
				throw new IllegalStateException("KuCoin takerFeeRate missing");
			}
			double maker = makerNode.asDouble();
			double taker = takerNode.asDouble();
			Instant ts = Instant.now();
			return new Fees(maker, taker, maker, taker, ts);
		}
	}

	public record TradingFeesSymbolsResponse(String code, String msg, JsonNode data) {
		public Map<String, Fees> getFeesBySymbols(List<String> symbols) {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin active contracts response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("KuCoin active contracts data missing");
			}

			Map<String, Fees> feesBySymbol = new HashMap<>();
			for (JsonNode contract : data) {
				String symbol = contract.path("symbol").asText();
				if (!symbols.contains(symbol)) continue;

				JsonNode makerNode = contract.get("makerFeeRate");
				JsonNode takerNode = contract.get("takerFeeRate");
				if (makerNode == null || makerNode.isNull()) continue;
				if (takerNode == null || takerNode.isNull()) continue;

				double maker = makerNode.asDouble();
				double taker = takerNode.asDouble();
				feesBySymbol.put(symbol, new Fees(maker, taker, maker, taker, Instant.now()));
			}
			return feesBySymbol;
		}
	}

	public record ChangeLeverageResponse(String code, String msg, JsonNode data) {
		public ChangeLeverageResponse {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin change leverage response code not OK: " + code + ", msg: " + msg);
			}
		}
	}

	public record SetMarginModeResponse(String code, String msg, JsonNode data) {
		public SetMarginModeResponse {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin set margin mode response code not OK: " + code + ", msg: " + msg);
			}
		}
	}

	public record SpotUsdtBalanceResponse(String code, String msg, JsonNode data) {
		public double get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin spot balance response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("KuCoin spot balance data missing");
			}
			for (JsonNode account : data) {
				String currency = account.path("currency").asText();
				String type = account.path("type").asText();
				if (!"USDT".equalsIgnoreCase(currency)) continue;
				if (!"main".equalsIgnoreCase(type)) continue;
				double available = account.path("available").asDouble();
				if (available == 0.0) {
					throw new IllegalStateException("USDT main account available balance missing in KuCoin spot balances");
				}

				return available;
			}
			throw new IllegalStateException("USDT main account not found in KuCoin spot balances");
		}
	}

	public record FuturesUsdtBalanceResponse(String code, String msg, JsonNode data) {
		public double get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin futures balance response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin futures balance data missing");
			}
			String currency = data.path("currency").asText();
			if (!"USDT".equalsIgnoreCase(currency)) {
				throw new IllegalStateException("Unexpected futures currency: " + currency);
			}

			double available = data.path("availableBalance").asDouble();
			if (available == 0.0) throw new IllegalStateException("KuCoin futures availableBalance missing");

			return available;
		}
	}

	public record MaxLeverageResponse(String code, String msg, JsonNode data) {
		public int get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin contract detail response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin contract detail data missing");
			}
			double maxLeverage = data.path("maxLeverage").asDouble();
			if (maxLeverage == 0.0) throw new IllegalStateException("KuCoin maxLeverage missing");

			return (int) Math.round(maxLeverage);
		}
	}

	public record SupportedChainsResponse(String code, String msg, JsonNode data) {
		public ExchangeChains get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin currency detail response code not OK: " + code + ", msg: " + msg);
			}
			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin currency detail data missing");
			}
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			JsonNode chains = data.get("chains");
			if (chains == null || !chains.isArray()) {
				throw new IllegalStateException("KuCoin currency chains missing");
			}
			for (JsonNode chain : chains) {
				String chainId = chain.path("chainId").asText();
				if (chainId.isEmpty()) continue;
				SupportedChain mapped = ChainsMap.getInverse(chainId);
				if (mapped == null) continue;

				boolean depositEnabled = chain.path("isDepositEnabled").asBoolean();
				boolean withdrawEnabled = chain.get("isWithdrawEnabled").asBoolean();
				if (depositEnabled) builder.addDepositableChain(mapped);
				if (withdrawEnabled && chain.has("withdrawalMinFee") && chain.has("withdrawalMinSize")) {
					double fee = chain.path("withdrawalMinFee").asDouble();
					double min = chain.path("withdrawalMinSize").asDouble();
					builder.addWithdrawableChain(new WithdrawChain(mapped, fee, min));
				}
			}
			return builder.build();
		}
	}

	public record UsdtWalletAddressResponse(String code, String msg, JsonNode data) {
		public WalletAddress get(SupportedChain chain) {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null || !data.isArray()) {
				throw new IllegalStateException("KuCoin deposit address data missing");
			}

			String targetChainId = ChainsMap.get(chain);
			for (JsonNode entry : data) {
				String chainId = entry.path("chainId").asText();
				if (chainId.isEmpty() || !chainId.equalsIgnoreCase(targetChainId)) continue;

				String address = entry.path("address").asText();
				if (address.isEmpty()) throw new IllegalStateException("KuCoin deposit address missing for chain: " + chain);

				String memo = entry.path("memo").asText();
				return new WalletAddress(chain, address, memo);
			}
			throw new IllegalStateException("USDT deposit address not found for chain: " + chain);
		}
	}

	public record WithdrawUsdtResponse(String code, String msg, JsonNode data) {
		public WithdrawUsdtResponse {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}
		}
	}

	public record PlaceFuturesOrderResponse(String code, String msg, JsonNode data) {
		public String orderId() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin order response data missing");
			}

			String orderId = data.path("orderId").asText();
			if (orderId.isEmpty()) throw new IllegalStateException("KuCoin orderId missing in response");

			return orderId;
		}
	}

	public record GetOrderRecordResponse(String code, String msg, JsonNode data) {
		public List<PartialFill> get() {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}

			if (data == null || !data.isObject()) {
				throw new IllegalStateException("KuCoin fills data missing");
			}

			JsonNode items = data.get("items");
			if (items == null || !items.isArray()) {
				throw new IllegalStateException("KuCoin fills items missing");
			}

			if (items.isEmpty()) return List.of();
			ArrayList<PartialFill> result = new ArrayList<>();
			for (JsonNode item : items) {
				String orderId = item.path("orderId").asText();
				if (orderId.isEmpty()) continue;

				String symbol = item.path("symbol").asText();
				if (symbol.isEmpty()) continue;

				double size = item.path("size").asDouble();
				if (size == 0.0) continue;

				double price = item.path("price").asDouble();
				if (price == 0.0) continue;

				double fee = item.path("fee").asDouble();
				if (fee == 0.0) continue;

				long tradeTime = item.path("createdAt").asLong();
				if (tradeTime == 0L) continue;

				Instant ts = Instant.ofEpochMilli(tradeTime);
				String feeCurrency = item.path("feeCurrency").asText();
				Double feeValue = "USDT".equalsIgnoreCase(feeCurrency) ? fee : null;
				result.add(new PartialFill(orderId, symbol, size, price, feeValue, ts));
			}
			return result;
		}
	}

	public record InternalTransferResponse(String code, String msg, JsonNode data) {
		public InternalTransferResponse {
			if (!expectedSuccessCode.equals(code)) {
				throw new IllegalStateException("KuCoin klines response code not OK: " + code + ", msg: " + msg);
			}
		}
	}
}
