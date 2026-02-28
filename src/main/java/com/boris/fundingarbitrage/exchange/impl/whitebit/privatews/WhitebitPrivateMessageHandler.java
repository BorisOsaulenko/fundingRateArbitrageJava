package com.boris.fundingarbitrage.exchange.impl.whitebit.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.privatews.PrivateMessageHandler;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

class WhitebitPrivateMessageHandler implements PrivateMessageHandler {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private DepositPatch parseDepositInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String method = root.path("method").asText();
		if (!"balanceSpot_update".equalsIgnoreCase(method)) return null;
		JsonNode params = root.get("params");
		if (params == null || !params.isArray() || params.isEmpty()) return null;
		JsonNode data = params.get(0);
		if (data == null || !data.isObject()) return null;
		JsonNode usdt = data.get("USDT");
		if (usdt == null || !usdt.isObject()) return null;
		String available = usdt.path("available").asText();
		if (available == null || available.isEmpty()) return null;
		BigDecimal free = new BigDecimal(available);
		if (free.compareTo(BigDecimal.ZERO) <= 0) return null;
		return new DepositPatch(free, Instant.now());
	}

	private PartialFill parsePartialFillInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String method = root.path("method").asText();
		if (!"deals_update".equalsIgnoreCase(method)) return null;
		JsonNode params = root.get("params");
		if (params == null || !params.isArray() || params.size() < 7) return null;

		String market = params.get(2).asText();
		String orderId = params.get(3).asText();
		if (orderId == null || orderId.isEmpty()) return null;

		BigDecimal price = new BigDecimal(params.get(4).asText());
		BigDecimal amount = new BigDecimal(params.get(5).asText());
		if (price.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(BigDecimal.ZERO) <= 0) return null;
		BigDecimal fee = new BigDecimal(params.get(6).asText());
		String feeAsset = params.size() > 10 ? params.get(10).asText() : null;
		BigDecimal feeValue = "USDT".equalsIgnoreCase(feeAsset) ? fee : null;

		BigDecimal time = new BigDecimal(params.get(1).asText());
		long timeMillis = time.multiply(BigDecimal.valueOf(1000L)).longValue();
		Instant ts = Instant.ofEpochMilli(timeMillis);

		return new PartialFill(orderId, market, amount, price, feeValue, ts);
	}

	private <T> T parseErrorHandled(java.util.function.Function<String, T> parser, String message) {
		try {
			return parser.apply(message);
		} catch (Exception ex) {
			Logger.log(ex.getMessage());
			return null;
		}
	}

	@Override
	public DepositPatch parseDepositMessageSymbol(String message) {
		return parseErrorHandled(
						(msg) -> {
							try {
								return parseDepositInternal(msg);
							} catch (JsonProcessingException e) {
								return null;
							}
						}, message
		);
	}

	@Override
	public PartialFill parsePartialFillMessageSymbol(String message) {
		return parseErrorHandled(
						(msg) -> {
							try {
								return parsePartialFillInternal(msg);
							} catch (JsonProcessingException e) {
								return null;
							}
						}, message
		);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		return null;
	}
}
