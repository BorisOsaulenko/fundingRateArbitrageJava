package com.boris.fundingarbitrage.exchange.impl.gate.privatews;

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

import java.time.Instant;

class GatePrivateMessageHandler implements PrivateMessageHandler {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private static double parseDouble(JsonNode node, String... fields) {
		for (String field : fields) {
			JsonNode val = node.get(field);
			if (val != null && !val.isNull()) {
				String text = val.asText();
				if (text != null && !text.isEmpty()) {
					try {
						return Double.parseDouble(text);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return 0.0;
	}

	private static Instant parseInstant(JsonNode node, String... fields) {
		for (String field : fields) {
			JsonNode val = node.get(field);
			if (val != null && !val.isNull()) {
				String text = val.asText();
				if (text != null && !text.isEmpty()) {
					try {
						long ts = Long.parseLong(text);
						if (ts > 10_000_000_000L) return Instant.ofEpochMilli(ts);
						return Instant.ofEpochSecond(ts);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return Instant.now();
	}

	private DepositPatch parseDepositInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		if (!"futures.balances".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) {
			return null;
		}
		JsonNode result = root.get("result");
		if (result == null || !result.isArray() || result.isEmpty()) return null;
		JsonNode entry = result.get(0);
		String currency = entry.path("currency").asText();
		if (!"USDT".equalsIgnoreCase(currency)) return null;
		double balance = parseDouble(entry, "available", "balance", "total");
		if (balance <= 0) return null;
		Instant ts = parseInstant(root, "time");
		return new DepositPatch(balance, ts);
	}

	private PartialFill parsePartialFillInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		if (!"futures.usertrades".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) {
			return null;
		}
		JsonNode result = root.get("result");
		if (result == null || !result.isArray() || result.isEmpty()) return null;
		JsonNode entry = result.get(0);
		String orderId = entry.path("order_id").asText();
		if (orderId == null || orderId.isEmpty()) return null;
		String symbol = entry.path("contract").asText();
		double size = parseDouble(entry, "size");
		double price = parseDouble(entry, "price");
		double fee = parseDouble(entry, "fee");
		Instant ts = parseInstant(entry, "create_time_ms", "create_time");
		Double feeValue = fee > 0 ? fee : null;
		return new PartialFill(orderId, symbol, Math.abs(size), price, feeValue, ts);
	}

	private <T> T parseErrorHandled(JsonParsingFunction<T> parser, String message) {
		try {
			return parser.apply(message);
		} catch (JsonParseException | JsonMappingException ex) {
			Logger.log(ex.getMessage());
			return null;
		} catch (Exception ex) {
			return null;
		}
	}

	@Override
	public DepositPatch parseDepositMessageSymbol(String message) {
		return parseErrorHandled(this::parseDepositInternal, message);
	}

	@Override
	public PartialFill parsePartialFillMessageSymbol(String message) {
		return parseErrorHandled(this::parsePartialFillInternal, message);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return null;
	}
}
