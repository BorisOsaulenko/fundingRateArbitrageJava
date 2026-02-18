package com.boris.fundingarbitrage.exchange.impl.bybit.privatews;

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

class BybitPrivateMessageHandler implements PrivateMessageHandler {
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
		String topic = root.path("topic").asText();
		if (!"wallet".equalsIgnoreCase(topic)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		JsonNode coins = entry.get("coin");
		if (coins == null || !coins.isArray()) return null;
		for (JsonNode coin : coins) {
			if (!"USDT".equalsIgnoreCase(coin.path("coin").asText())) continue;
			double balance = parseDouble(coin, "walletBalance", "availableToWithdraw", "availableBalance");
			if (balance <= 0) return null;
			Instant ts = parseInstant(root, "creationTime", "ts");
			return new DepositPatch(balance, ts);
		}
		return null;
	}

	private PartialFill parsePartialFillInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String topic = root.path("topic").asText();
		if (!"execution".equalsIgnoreCase(topic)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isArray() || data.isEmpty()) return null;
		JsonNode entry = data.get(0);
		String orderId = entry.path("orderId").asText();
		if (orderId == null || orderId.isEmpty()) return null;
		String symbol = entry.path("symbol").asText();
		double qty = parseDouble(entry, "execQty", "qty");
		double price = parseDouble(entry, "execPrice", "price");
		double fee = parseDouble(entry, "execFee", "fee");
		String feeCoin = entry.path("feeCurrency").asText();
		Double feeValue = "USDT".equalsIgnoreCase(feeCoin) ? fee : null;
		Instant ts = parseInstant(entry, "execTime", "tradeTime", "ts");
		return new PartialFill(orderId, symbol, qty, price, feeValue, ts);
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
