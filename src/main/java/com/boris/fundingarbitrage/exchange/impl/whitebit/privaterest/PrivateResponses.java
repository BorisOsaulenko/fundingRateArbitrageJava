package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PrivateResponses {
	private static double parseRequiredDouble(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) throw new IllegalStateException("Missing field: " + field);
		String text = val.asText();
		if (text == null || text.isEmpty()) throw new IllegalStateException("Missing field: " + field);
		return Double.parseDouble(text);
	}

	public record TradingFeesResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public TradingFeesResponse {}

		public Fees getFees() {
			double maker = parseRequiredDouble(node, "futures_maker") / 100;
			double taker = parseRequiredDouble(node, "futures_taker") / 100;
			return new Fees(maker, taker, maker, taker, Instant.now());
		}
	}

	public record SpotBalanceResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public SpotBalanceResponse {}

		public double usdtAvailable() {
			if (node == null || !node.isObject()) throw new IllegalStateException("Balance response missing");
			JsonNode usdt = node.get("USDT");
			if (usdt == null || !usdt.isObject()) throw new IllegalStateException("USDT balance not found");
			return parseRequiredDouble(usdt, "available");
		}
	}

	public record CollateralSummaryResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public CollateralSummaryResponse {}

		public double futuresBalance() {
			if (node == null || !node.isObject()) {
				throw new IllegalStateException("Collateral balance missing");
			}
			JsonNode usdt = node.get("USDT");
			if (usdt == null || usdt.isNull()) throw new IllegalStateException("USDT collateral balance missing");
			if (usdt.isNumber()) return usdt.asDouble();
			if (usdt.isTextual()) return Double.parseDouble(usdt.asText());
			throw new IllegalStateException("Invalid USDT collateral balance");
		}
	}

	public record WalletAddressResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public WalletAddressResponse {}

		public WalletAddress get(SupportedChain chain) {
			if (node == null || !node.isObject()) throw new IllegalStateException("Wallet address response missing");
			JsonNode account = node.get("account");
			if (account == null || !account.isObject()) throw new IllegalStateException("Account info missing");
			String address = account.path("address").asText();
			if (address == null || address.isEmpty()) throw new IllegalStateException("Missing address");
			String memo = account.path("memo").asText(null);
			if (memo != null && memo.isEmpty()) memo = null;
			return new WalletAddress(chain, address, memo);
		}
	}

	public record PlaceOrderResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public PlaceOrderResponse {}

		public String orderId() {
			if (node == null || !node.isObject()) throw new IllegalStateException("Order response missing");
			String orderId = node.path("orderId").asText();
			if (orderId == null || orderId.isEmpty()) throw new IllegalStateException("Missing orderId");
			return orderId;
		}
	}

	public record OrderDealsResponse(JsonNode node) {
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public OrderDealsResponse {}

		public List<PartialFill> get() {
			if (node == null || !node.isObject()) throw new IllegalStateException("Order deals response missing");
			JsonNode records = node.get("records");
			if (records == null || !records.isArray()) {
				throw new IllegalStateException("Order deals records missing");
			}
			ArrayList<PartialFill> result = new ArrayList<>();
			for (JsonNode item : records) {
				String orderId = item.path("dealOrderId").asText();
				if (orderId == null || orderId.isEmpty()) continue;
				String symbol = item.path("market").asText();
				if (symbol == null || symbol.isEmpty()) throw new IllegalStateException("Missing market");
				double amount = parseRequiredDouble(item, "amount");
				double price = parseRequiredDouble(item, "price");
				double fee = parseRequiredDouble(item, "fee");
				String feeAsset = item.path("feeAsset").asText();
				Double feeValue = "USDT".equalsIgnoreCase(feeAsset) ? fee : null;
				double time = item.path("time").asDouble();
				if (time == 0.0) throw new IllegalStateException("Missing time");
				long timeMillis = (long) (time * 1000.0);
				Instant ts = Instant.ofEpochMilli(timeMillis);
				result.add(new PartialFill(orderId, symbol, amount, price, feeValue, ts));
			}
			return result;
		}
	}
}
