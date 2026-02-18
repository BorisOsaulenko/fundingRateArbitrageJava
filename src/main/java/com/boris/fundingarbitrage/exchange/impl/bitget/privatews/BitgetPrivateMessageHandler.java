package com.boris.fundingarbitrage.exchange.impl.bitget.privatews;

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

class BitgetPrivateMessageHandler implements PrivateMessageHandler {
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
						return Instant.ofEpochMilli(Long.parseLong(text));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return Instant.now();
	}

	private DepositPatch parseDepositInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		JsonNode arg = root.get("arg");
		if (arg == null || !"account".equalsIgnoreCase(arg.path("channel").asText())) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		String marginCoin = entry.path("marginCoin").asText();
		if (!"USDT".equalsIgnoreCase(marginCoin)) return null;
		double available = parseDouble(entry, "available", "availableBalance", "availableEquity", "maxAvailable");
		if (available <= 0) return null;
		Instant ts = parseInstant(entry, "ts", "timestamp");
		return new DepositPatch(available, ts);
	}

	private PartialFill parsePartialFillInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		JsonNode arg = root.get("arg");
		if (arg == null || !"fill".equalsIgnoreCase(arg.path("channel").asText())) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		String orderId = entry.path("orderId").asText();
		if (orderId == null || orderId.isEmpty()) return null;
		String symbol = entry.path("symbol").asText();
		double size = parseDouble(entry, "size", "tradeSize", "baseVolume");
		double price = parseDouble(entry, "price", "tradePrice");
		double fee = parseDouble(entry, "fee");
		String feeCoin = entry.path("feeCoin").asText();
		Double feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
		Instant ts = parseInstant(entry, "ts", "fillTime", "cTime");
		return new PartialFill(orderId, symbol, size, price, feeValue, ts);
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
