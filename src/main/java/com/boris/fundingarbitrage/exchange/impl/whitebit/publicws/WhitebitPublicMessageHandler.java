package com.boris.fundingarbitrage.exchange.impl.whitebit.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public class WhitebitPublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public WhitebitPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		String method = root.path("method").asText();
		if (!"bookTicker_update".equalsIgnoreCase(method)) return null;
		JsonNode params = root.get("params");
		if (params == null || !params.isArray() || params.isEmpty()) return null;
		JsonNode data = params.get(0);
		if (data == null || !data.isArray() || data.size() < 8) return null;

		String symbol = data.get(2).asText();
		if (symbol == null || symbol.isEmpty()) return null;
		String coin = context.getSymbolInverse(symbol);

		double bidPrice = data.get(4).asDouble();
		double bidSize = data.get(5).asDouble();
		double askPrice = data.get(6).asDouble();
		double askSize = data.get(7).asDouble();
		if (bidPrice == 0.0 || bidSize == 0.0 || askPrice == 0.0 || askSize == 0.0) return null;

		double messageTime = data.get(1).asDouble();
		long tsMillis = (long) (messageTime * 1000.0);
		Instant ts = Instant.ofEpochMilli(tsMillis);

		return new BookTickerPatch(coin, bidPrice, bidSize, askPrice, askSize, ts);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String method = root.path("method").asText();
		if (!"lastprice_update".equalsIgnoreCase(method)) return null;
		JsonNode params = root.get("params");
		if (params == null || !params.isArray() || params.size() < 2) return null;

		String symbol = params.get(0).asText();
		if (symbol == null || symbol.isEmpty()) return null;
		String coin = context.getSymbolInverse(symbol);

		double lastPrice = Double.parseDouble(params.get(1).asText());
		if (lastPrice == 0.0) return null;
		return new MarkPricePatch(coin, lastPrice, Instant.now());
	}

	private <T> T parseErrorHandled(java.util.function.Function<JsonNode, T> parser, JsonNode root) {
		try {
			return parser.apply(root);
		} catch (IllegalArgumentException ex) {
			Logger.log(ex.getMessage());
			return null;
		} catch (Exception ex) {
			return null;
		}
	}

	@Override
	public FundingRatePatch parseFundingRateMessageSymbol(JsonNode root) {
		return null;
	}

	@Override
	public BookTickerPatch parseBookTickerMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseBookTickerInternal, root);
	}

	@Override
	public MarkPricePatch parseMarkPriceMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseMarkPriceInternal, root);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		try {
			JsonNode root = mapper.readTree(trimmed);
			String method = root.path("method").asText();
			if (!"ping".equalsIgnoreCase(method)) return null;
			long id = root.path("id").asLong();
			return String.format("{\"id\":%d,\"result\":\"pong\",\"error\":null}", id);
		} catch (JsonProcessingException ignored) {
			return null;
		}
	}
}
