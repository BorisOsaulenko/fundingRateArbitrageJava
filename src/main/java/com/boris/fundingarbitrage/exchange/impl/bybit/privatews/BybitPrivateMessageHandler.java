package com.boris.fundingarbitrage.exchange.impl.bybit.privatews;

import com.boris.fundingarbitrage.exchange.privatews.PrivateMessageHandler;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

class BybitPrivateMessageHandler implements PrivateMessageHandler {
	private static BigDecimal parseBigDecimal(JsonNode node, String... fields) {
		for (String field : fields) {
			JsonNode val = node.get(field);
			if (val != null && !val.isNull()) {
				String text = val.asText();
				if (text != null && !text.isEmpty()) {
					try {
						return new BigDecimal(text);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return BigDecimal.ZERO;
	}

	private static Instant parseInstant(JsonNode node, String... fields) {
		for (String field : fields) {
			JsonNode val = node.get(field);
			if (val != null && !val.isNull()) {
				String text = val.asText();
				if (text != null && !text.isEmpty()) {
					try {
						return Instant.ofEpochMilli(Long.parseLong(text));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return Instant.now();
	}

	@Override
	public DepositPatch parseDepositMessageSymbol(JsonNode root) {
		String topic = root.path("topic").asText();
		if (!"wallet".equalsIgnoreCase(topic)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		JsonNode coins = entry.get("coin");
		if (coins == null || !coins.isArray()) return null;
		for (JsonNode coin : coins) {
			if (!"USDT".equalsIgnoreCase(coin.path("coin").asText())) continue;
			BigDecimal balance = parseBigDecimal(coin, "walletBalance", "availableToWithdraw", "availableBalance");
			if (balance.compareTo(BigDecimal.ZERO) <= 0) return null;
			Instant ts = parseInstant(root, "creationTime", "ts");
			return new DepositPatch(balance, ts);
		}
		return null;
	}

	@Override
	public PartialFill parsePartialFillMessageSymbol(JsonNode root) {
		String topic = root.path("topic").asText();
		if (!"execution".equalsIgnoreCase(topic)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		String orderId = entry.path("orderId").asText();
		if (orderId == null || orderId.isEmpty()) return null;
		String symbol = entry.path("symbol").asText();
		BigDecimal qty = parseBigDecimal(entry, "execQty", "qty");
		BigDecimal price = parseBigDecimal(entry, "execPrice", "price");
		BigDecimal fee = parseBigDecimal(entry, "execFee", "fee");
		String feeCoin = entry.path("feeCurrency").asText();
		BigDecimal feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
		Instant ts = parseInstant(entry, "execTime", "tradeTime", "ts");
		return new PartialFill(orderId, symbol, qty, price, feeValue, ts);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		return null;
	}
}
