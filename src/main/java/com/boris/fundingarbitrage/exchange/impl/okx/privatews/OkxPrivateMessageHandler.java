package com.boris.fundingarbitrage.exchange.impl.okx.privatews;

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

class OkxPrivateMessageHandler implements PrivateMessageHandler {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private static Double parseDouble(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return null;
		String text = val.asText();
		if (text == null || text.isEmpty()) return null;
		double parsed = Double.parseDouble(text);
		if (parsed == 0.0) return null;
		return parsed;
	}

	private static Instant parseInstant(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return null;
		String text = val.asText();
		if (text == null || text.isEmpty()) return null;
		return Instant.ofEpochMilli(Long.parseLong(text));
	}

	private DepositPatch parseDepositInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String channel = root.path("arg").path("channel").asText();
		if (!"deposit-info".equalsIgnoreCase(channel)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		if (!"USDT".equalsIgnoreCase(entry.path("ccy").asText())) return null;
		Double amt = parseDouble(entry, "amt");
		Instant ts = parseInstant(entry, "ts");
		if (amt == null || ts == null) return null;
		return new DepositPatch(amt, ts);
	}

	private PartialFill parsePartialFillInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String channel = root.path("arg").path("channel").asText();
		if (!"orders".equalsIgnoreCase(channel)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		String orderId = entry.path("ordId").asText();
		if (orderId == null || orderId.isEmpty()) return null;
		String symbol = entry.path("instId").asText();
		Double qty = parseDouble(entry, "fillSz");
		Double price = parseDouble(entry, "fillPx");
		Double fee = null;
		String feeCcy = entry.path("feeCcy").asText();
		Double feeParsed = parseDouble(entry, "fee");
		if (feeParsed != null && "USDT".equalsIgnoreCase(feeCcy)) fee = feeParsed;
		Instant ts = parseInstant(entry, "fillTime");
		if (ts == null) ts = parseInstant(entry, "uTime");
		if (symbol == null || symbol.isEmpty() || qty == null || price == null || ts == null) return null;
		return new PartialFill(orderId, symbol, qty, price, fee, ts);
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
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		return null;
	}
}
