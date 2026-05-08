package com.boris.fundingarbitrage.exchange.impl.binance.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.privatews.PrivateMessageHandler;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

class BinancePrivateMessageHandler implements PrivateMessageHandler {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	@Override
	public DepositPatch parseDepositMessageSymbol(JsonNode root) {
		JsonNode eventNode = root.has("event") ? root.get("event") : root;
		String eventType = eventNode.path("e").asText();
		if (!"outboundAccountPosition".equals(eventType)) return null;
		JsonNode balances = eventNode.get("B");
		if (balances == null || !balances.isArray()) return null;
		for (JsonNode balance : balances) {
			if (!"USDT".equals(balance.path("a").asText())) continue;
			BigDecimal free = balance.path("f").decimalValue();
			if (free.compareTo(BigDecimal.ZERO) > 0) {
				long timestamp = eventNode.path("E").asLong(0L);
				return new DepositPatch(free, Instant.ofEpochMilli(timestamp));
			}
		}
		return null;
	}

	@Override
	public PartialFill parsePartialFillMessageSymbol(JsonNode root) {
		JsonNode eventNode = root.has("event") ? root.get("event") : root;
		String eventType = eventNode.path("e").asText();
		if (!"executionReport".equals(eventType)) return null;
		String symbol = eventNode.path("s").asText();
		if (!"USDT".equals(symbol)) return null;
		return new PartialFill(
						String.valueOf(eventNode.path("i").asLong()),
						symbol,
						eventNode.path("l").decimalValue(),
						eventNode.path("L").decimalValue(),
						eventNode.path("n").decimalValue(),
						Instant.ofEpochMilli(eventNode.path("E").asLong())
		);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return null;
	}
}
