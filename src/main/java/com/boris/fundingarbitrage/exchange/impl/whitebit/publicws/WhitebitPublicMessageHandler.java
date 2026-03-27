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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

class WhitebitPublicMessageHandler implements PublicMessageHandler {
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
		String coin = context.getFuturesSymbolInverse(symbol);

		String bidPriceNode = data.get(4).asText();
		String bidSizeNode = data.get(5).asText();
		String askPriceNode = data.get(6).asText();
		String askSizeNode = data.get(7).asText();
		BigDecimal bidPrice = bidPriceNode.isEmpty() ? null : new BigDecimal(bidPriceNode);
		BigDecimal bidSize = bidSizeNode.isEmpty() ? null : new BigDecimal(bidSizeNode);
		BigDecimal askPrice = askPriceNode.isEmpty() ? null : new BigDecimal(askPriceNode);
		BigDecimal askSize = askSizeNode.isEmpty() ? null : new BigDecimal(askSizeNode);
		if (bidPrice == null && bidSize == null && askPrice == null && askSize == null) return null;

		BigDecimal messageTime = new BigDecimal(data.get(1).asText());
		long tsMillis = messageTime.multiply(BigDecimal.valueOf(1000L)).longValue();
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
		String coin = context.getFuturesSymbolInverse(symbol);

		String lastPriceNode = params.get(1).asText(); // whitebit has no mark price indicator
		BigDecimal lastPrice = lastPriceNode.isEmpty() ? null : new BigDecimal(lastPriceNode);
		if (lastPrice == null) return null;

		return new MarkPricePatch(coin, lastPrice, Instant.now());
	}

	private <T> T parseErrorHandled(Function<JsonNode, T> parser, JsonNode root) {
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
	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseBookTickerInternal, root);
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
