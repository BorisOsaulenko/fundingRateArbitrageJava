package com.boris.fundingarbitrage.exchange.impl.okx.privaterest;

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
	private static void ensureOk(String code, String msg) {
		if (!"0".equals(code)) {
			throw new RuntimeException(String.format("OKX private request failed: %s, %s", code, msg));
		}
	}

	private static boolean parseBoolean(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return false;
		if (val.isBoolean()) return val.asBoolean();
		String text = val.asText();
		if ("true".equalsIgnoreCase(text)) return true;
		if ("false".equalsIgnoreCase(text)) return false;
		return "1".equals(text);
	}

	public record TradingFeesResponse(String code, String msg, JsonNode data) {
		public Fees getFees() {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX trade fee data missing");
			}
			JsonNode item = data.get(0);

			JsonNode makerNode = item.get("makerU");
			JsonNode takerNode = item.get("takerU");
			if (makerNode == null || makerNode.isNull() || takerNode == null || takerNode.isNull()) {
				throw new IllegalStateException("OKX makerU/takerU missing");
			}

			double taker = -takerNode.asDouble(); // OKX returns negative values for fees, we want positive
			double maker = -makerNode.asDouble();

			return new Fees(maker, taker, maker, taker, Instant.now());
		}
	}

	public record TradingFeesSymbolsResponse(String code, String msg, JsonNode data) {
		public Map<String, Fees> getFeesBySymbols(List<String> symbols) {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX trade fee data missing");
			}
			JsonNode item = data.get(0);
			JsonNode makerNode = item.get("makerU");
			JsonNode takerNode = item.get("takerU");
			if (makerNode == null || makerNode.isNull() || takerNode == null || takerNode.isNull()) {
				throw new IllegalStateException("OKX makerU/takerU missing");
			}
			double maker = -makerNode.asDouble();
			double taker = -takerNode.asDouble();
			Fees fees = new Fees(maker, taker, maker, taker, Instant.now());

			Map<String, Fees> feesBySymbol = new HashMap<>();
			for (String symbol : symbols) {
				feesBySymbol.put(symbol, fees);
			}
			return feesBySymbol;
		}
	}

	public record ChangeLeverageResponse(String code, String msg) {
		public ChangeLeverageResponse {
			ensureOk(code, msg);
		}
	}

	public record LeverageInfoResponse(String code, String msg, JsonNode data) {
		public int getLever(String symbol) {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX leverage info missing");
			}
			for (JsonNode item : data) {
				if (!symbol.equalsIgnoreCase(item.path("instId").asText())) continue;
				String lever = item.path("lever").asText();
				if (lever == null || lever.isEmpty()) {
					throw new IllegalStateException("OKX leverage value missing");
				}
				return (int) Math.floor(Double.parseDouble(lever));
			}
			throw new IllegalStateException("OKX leverage info not found for symbol: " + symbol);
		}
	}

	public record SpotUsdtBalanceResponse(String code, String msg, JsonNode data) {
		public double get() {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX spot balance data missing");
			}
			for (JsonNode item : data) {
				if (!"USDT".equalsIgnoreCase(item.path("ccy").asText())) continue;
				String bal = item.path("bal").asText();
				if (bal == null || bal.isEmpty()) {
					throw new IllegalStateException("OKX spot balance missing for USDT");
				}
				return Double.parseDouble(bal);
			}
			throw new IllegalStateException("OKX spot USDT balance not found");
		}
	}

	public record FuturesUsdtBalanceResponse(String code, String msg, JsonNode data) {
		public double get() {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX futures balance data missing");
			}
			JsonNode account = data.get(0);
			JsonNode details = account.get("details");
			if (details == null || !details.isArray()) {
				throw new IllegalStateException("OKX futures balance details missing");
			}
			for (JsonNode item : details) {
				if (!"USDT".equalsIgnoreCase(item.path("ccy").asText())) continue;
				String eq = item.path("eq").asText();
				if (eq == null || eq.isEmpty()) {
					throw new IllegalStateException("OKX futures equity missing for USDT");
				}
				return Double.parseDouble(eq);
			}
			throw new IllegalStateException("OKX futures USDT balance not found");
		}
	}

	public record MaxLeverageResponse(String code, String msg, JsonNode data) {
		public int get(String symbol) {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX instrument data missing");
			}
			for (JsonNode item : data) {
				if (!symbol.equalsIgnoreCase(item.path("instId").asText())) continue;
				String lever = item.path("lever").asText();
				if (lever == null || lever.isEmpty()) {
					throw new IllegalStateException("OKX instrument lever missing");
				}
				return (int) Math.floor(Double.parseDouble(lever));
			}
			throw new IllegalStateException("OKX instrument not found for symbol: " + symbol);
		}
	}

	public record SupportedChainsResponse(String code, String msg, JsonNode data) {
		public ExchangeChains get() {
			ensureOk(code, msg);
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("OKX currencies data missing");
			}
			ExchangeChainsBuilder builder = new ExchangeChainsBuilder();
			for (JsonNode item : data) {
				String chain = item.path("chain").asText();
				if (chain == null || chain.isEmpty()) continue;
				SupportedChain mapped = ChainsMap.getInverse(chain);
				if (mapped == null) continue;
				boolean canDep = parseBoolean(item, "canDep");
				boolean canWd = parseBoolean(item, "canWd");
				if (canDep) builder.addDepositableChain(mapped);
				if (canWd) {
					double fee = item.path("fee").asDouble();
					if (fee <= 0.0) throw new IllegalStateException("OKX chain fee missing for chain: " + chain);

					double minWd = item.path("minWd").asDouble();
					if (minWd <= 0.0) throw new IllegalStateException("OKX chain minWd missing for chain: " + chain);

					builder.addWithdrawableChain(new WithdrawChain(mapped, fee, minWd));
				}
			}
			return builder.build();
		}
	}

	public record UsdtWalletAddressResponse(String code, String msg, JsonNode data) {
		public WalletAddress get(SupportedChain chain) {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX deposit address data missing");
			}
			String chainName = ChainsMap.get(chain);
			for (JsonNode item : data) {
				if (!chainName.equalsIgnoreCase(item.path("chain").asText())) continue;
				String addr = item.path("addr").asText();
				if (addr == null || addr.isEmpty()) {
					throw new IllegalStateException("OKX deposit address missing");
				}
				String tag = item.path("tag").asText();
				String memo = (tag == null || tag.isEmpty()) ? null : tag;
				return new WalletAddress(chain, addr, memo);
			}
			throw new IllegalStateException("OKX deposit address not found for chain: " + chain);
		}
	}

	public record WithdrawUsdtResponse(String code, String msg) {
		public WithdrawUsdtResponse {
			ensureOk(code, msg);
		}
	}

	public record PlaceFuturesOrderResponse(String code, String msg, JsonNode data) {
		public String orderId() {
			ensureOk(code, msg);
			if (data == null || !data.isArray() || data.isEmpty()) {
				throw new IllegalStateException("OKX order response missing");
			}
			String ordId = data.get(0).path("ordId").asText();
			if (ordId == null || ordId.isEmpty()) {
				throw new IllegalStateException("OKX ordId missing");
			}
			return ordId;
		}
	}

	public record GetOrderRecordResponse(String code, String msg, JsonNode data) {
		public List<PartialFill> get(String orderId) {
			ensureOk(code, msg);
			List<PartialFill> fills = new ArrayList<>();
			if (data == null || !data.isArray()) return fills;
			for (JsonNode item : data) {
				String ordId = item.path("ordId").asText();
				if (!orderId.equals(ordId)) continue;
				String symbol = item.path("instId").asText();
				String szText = item.path("fillSz").asText();
				String pxText = item.path("fillPx").asText();
				String feeText = item.path("fee").asText();
				String feeCcy = item.path("feeCcy").asText();
				String tsText = item.path("ts").asText();
				if (symbol == null || symbol.isEmpty()) continue;
				if (szText == null || szText.isEmpty()) continue;
				if (pxText == null || pxText.isEmpty()) continue;
				if (tsText == null || tsText.isEmpty()) continue;
				double qty = Double.parseDouble(szText);
				double price = Double.parseDouble(pxText);
				Double fee = null;
				if (feeText != null && !feeText.isEmpty() && "USDT".equalsIgnoreCase(feeCcy)) {
					fee = Double.parseDouble(feeText);
				}
				Instant ts = Instant.ofEpochMilli(Long.parseLong(tsText));
				fills.add(new PartialFill(orderId, symbol, qty, price, fee, ts));
			}
			return fills;
		}
	}

	public record InternalTransferResponse(String code, String msg) {
		public InternalTransferResponse {
			ensureOk(code, msg);
		}
	}

	public record CurrencyInfoResponse(String code, String msg, JsonNode data) {
		public String minFee(SupportedChain chain) {
			ensureOk(code, msg);
			if (data == null || !data.isArray()) {
				throw new IllegalStateException("OKX currencies data missing");
			}
			String chainName = ChainsMap.get(chain);
			for (JsonNode item : data) {
				if (!chainName.equalsIgnoreCase(item.path("chain").asText())) continue;
				String minFee = item.path("minFee").asText();
				if (minFee == null || minFee.isEmpty()) {
					throw new IllegalStateException("OKX minFee missing for chain: " + chain);
				}
				return minFee;
			}
			throw new IllegalStateException("OKX currency info not found for chain: " + chain);
		}
	}
}
