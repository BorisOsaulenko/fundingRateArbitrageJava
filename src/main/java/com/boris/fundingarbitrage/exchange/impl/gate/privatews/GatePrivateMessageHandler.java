package com.boris.fundingarbitrage.exchange.impl.gate.privatews;

import com.boris.fundingarbitrage.exchange.privatews.PrivateMessageHandler;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

class GatePrivateMessageHandler implements PrivateMessageHandler {
	@Override
	public DepositPatch parseDepositMessageSymbol(JsonNode root) {
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		if (!"futures.balances".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) return null;

		JsonNode result = root.get("result");
		if (result == null || !result.isArray() || result.isEmpty()) return null;
		JsonNode entry = result.get(0);
		String currency = entry.path("currency").asText();
		if (!"USDT".equalsIgnoreCase(currency)) return null;
		String balance = entry.path("balance").asText();
		long timestamp = entry.path("time_ms").asLong();
		if (balance.isBlank() || timestamp == 0) return null;
		return new DepositPatch(new BigDecimal(balance), Instant.ofEpochMilli(timestamp));
	}

	@Override
	public PartialFill parsePartialFillMessageSymbol(JsonNode root) {
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		if (!"futures.usertrades".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) return null;

		JsonNode result = root.get("result");
		if (result == null || !result.isArray() || result.isEmpty()) return null;
		JsonNode entry = result.get(0);
		String orderId = entry.path("order_id").asText();
		if (orderId == null || orderId.isEmpty()) return null;
		String symbol = entry.path("contract").asText();
		String size = entry.get("size").asText();
		String price = entry.get("price").asText();
		String fee = entry.get("fee").asText();
		long timestamp = entry.get("create_time_ms").asLong();
		if (symbol.isBlank() || size.isBlank() || price.isBlank() || fee.isBlank() || timestamp == 0) return null;
		return new PartialFill(
						orderId,
						symbol,
						new BigDecimal(size),
						new BigDecimal(price),
						new BigDecimal(fee),
						Instant.ofEpochMilli(timestamp)
		);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return null;
	}
}
