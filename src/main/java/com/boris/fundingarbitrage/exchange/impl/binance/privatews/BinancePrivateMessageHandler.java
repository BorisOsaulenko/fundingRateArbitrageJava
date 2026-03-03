package com.boris.fundingarbitrage.exchange.impl.binance.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.privatews.PrivateMessageHandler;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.boris.fundingarbitrage.util.JsonParsingFunction;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

class BinancePrivateMessageHandler implements PrivateMessageHandler {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private static double parseDouble(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return 0.0;
		String text = val.asText();
		if (text == null || text.isEmpty()) return 0.0;
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException ignored) {
			return 0.0;
		}
	}

	private DepositPatch parseDepositInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
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

	private PartialFill parsePartialFillInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
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

	private <T> T parseErrorHandled(JsonParsingFunction<T> parser, String message) {
		try {
			return parser.apply(message);
		} catch (JsonParseException | JsonMappingException ex) {
			Logger.log(ex.getMessage());
			return null; // Not a BookTicker message
		} catch (Exception ex) {
			return null;
		}
	}

	@Override public DepositPatch parseDepositMessageSymbol(String message) {
		return parseErrorHandled(this::parseDepositInternal, message);
	}

	@Override public PartialFill parsePartialFillMessageSymbol(String message) {
		return parseErrorHandled(this::parsePartialFillInternal, message);
	}

	@Override public String getResponseToPingMessage(String message) {
		return null;
	}
}
