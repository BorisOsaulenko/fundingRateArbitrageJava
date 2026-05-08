package com.boris.fundingarbitrage.exchange.impl.okx.privatews;

import com.boris.fundingarbitrage.exchange.privatews.PrivateMessageHandler;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

class OkxPrivateMessageHandler implements PrivateMessageHandler {
	private static BigDecimal parseBigDecimal(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return null;
		String text = val.asText();
		if (text == null || text.isEmpty()) return null;
		BigDecimal parsed = new BigDecimal(text);
		if (parsed.compareTo(BigDecimal.ZERO) == 0) return null;
		return parsed;
	}

	private static Instant parseInstant(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return null;
		String text = val.asText();
		if (text == null || text.isEmpty()) return null;
		return Instant.ofEpochMilli(Long.parseLong(text));
	}

	@Override
	public DepositPatch parseDepositMessageSymbol(JsonNode root) {
		String channel = root.path("arg").path("channel").asText();
		if (!"deposit-info".equalsIgnoreCase(channel)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		if (!"USDT".equalsIgnoreCase(entry.path("ccy").asText())) return null;
		BigDecimal amt = parseBigDecimal(entry, "amt");
		Instant ts = parseInstant(entry, "ts");
		if (amt == null || ts == null) return null;
		return new DepositPatch(amt, ts);
	}

	@Override
	public PartialFill parsePartialFillMessageSymbol(JsonNode root) {
		String channel = root.path("arg").path("channel").asText();
		if (!"orders".equalsIgnoreCase(channel)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		String orderId = entry.path("ordId").asText();
		if (orderId == null || orderId.isEmpty()) return null;
		String symbol = entry.path("instId").asText();
		BigDecimal qty = parseBigDecimal(entry, "fillSz");
		BigDecimal price = parseBigDecimal(entry, "fillPx");
		BigDecimal fee = null;
		String feeCcy = entry.path("feeCcy").asText();
		BigDecimal feeParsed = parseBigDecimal(entry, "fee");
		if (feeParsed != null && "USDT".equalsIgnoreCase(feeCcy)) fee = feeParsed;
		Instant ts = parseInstant(entry, "fillTime");
		if (ts == null) ts = parseInstant(entry, "uTime");
		if (symbol == null || symbol.isEmpty() || qty == null || price == null || ts == null) return null;
		return new PartialFill(orderId, symbol, qty, price, fee, ts);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		return null;
	}
}
