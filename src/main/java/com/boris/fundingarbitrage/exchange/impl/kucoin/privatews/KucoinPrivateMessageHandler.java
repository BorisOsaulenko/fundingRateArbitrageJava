package com.boris.fundingarbitrage.exchange.impl.kucoin.privatews;

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

public class KucoinPrivateMessageHandler implements PrivateMessageHandler {
	private static final String WALLET_TOPIC = "/contractAccount/wallet";
	private static final String ORDERS_TOPIC = "/contractMarket/tradeOrders";
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private boolean isInvalidType(JsonNode root) {
		String type = root.path("type").asText();
		return type != null && !type.isEmpty() && !"message".equalsIgnoreCase(type);
	}

	private DepositPatch parseDepositInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		if (isInvalidType(root)) return null;
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(WALLET_TOPIC)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String currency = data.path("currency").asText();
		if (!"USDT".equalsIgnoreCase(currency)) return null;

		String availableText = data.path("availableBalance").asText();
		String timestampText = data.path("timestamp").asText();
		if (availableText == null || availableText.isEmpty()) return null;
		if (timestampText == null || timestampText.isEmpty()) return null;

		double available = Double.parseDouble(availableText);
		if (available <= 0) return null;
		long ts = Long.parseLong(timestampText);
		return new DepositPatch(available, Instant.ofEpochMilli(ts));
	}

	private PartialFill parsePartialFillInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		if (isInvalidType(root)) return null;
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(ORDERS_TOPIC)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String eventType = data.path("type").asText();
		if (!"match".equalsIgnoreCase(eventType)) return null;

		String orderId = data.path("orderId").asText();
		String symbol = data.path("symbol").asText();
		String matchSizeText = data.path("matchSize").asText();
		String matchPriceText = data.path("matchPrice").asText();
		long ts = data.path("ts").asLong();
		if (orderId.isEmpty()) return null;
		if (symbol.isEmpty()) return null;
		if (matchSizeText.isEmpty()) return null;
		if (matchPriceText.isEmpty()) return null;
		if (ts == 0) return null;

		double size = Double.parseDouble(matchSizeText);
		double price = Double.parseDouble(matchPriceText);
		Instant timestamp = Instant.ofEpochMilli(ts / 1000_000); // KuCoin ts is in nanoseconds, convert to milliseconds
		return new PartialFill(orderId, symbol, size, price, null, timestamp);
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
		if (message == null || message.isEmpty()) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		try {
			JsonNode root = mapper.readTree(message);
			String type = root.path("type").asText();
			if (!"ping".equalsIgnoreCase(type)) return null;
			String id = root.path("id").asText();
			if (id.isEmpty()) return null;

			PongResponse response = new PongResponse(id);
			return mapper.writeValueAsString(response);
		} catch (Exception ex) {
			return null;
		}
	}
}
