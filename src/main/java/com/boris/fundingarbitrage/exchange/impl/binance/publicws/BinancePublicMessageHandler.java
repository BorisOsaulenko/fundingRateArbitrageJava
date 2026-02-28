package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

class BinancePublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;
	private final ObjectMapper jsonMapper = ObjectMapperSingleton.getInstance();

	public BinancePublicMessageHandler(ExchangeContext exchangeContext) {
		this.context = exchangeContext;
	}

	private FundingRatePatch parseFundingRateInternal(JsonNode root) {
		String symbol = root.path("s").asText();
		if (symbol.isEmpty()) return null;

		JsonNode rateNode = root.get("r");
		if (rateNode == null) return null;
		BigDecimal rate = rateNode.decimalValue();

		long settlementTime = root.path("T").asLong();
		if (settlementTime == 0) return null;

		long eventTime = root.path("E").asLong();
		if (eventTime == 0) return null;

		String coin = context.getSymbolInverse(symbol);

		return new FundingRatePatch(coin, rate, Instant.ofEpochMilli(settlementTime), Instant.ofEpochMilli(eventTime));
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String symbol = root.path("s").asText();
		if (symbol.isEmpty()) return null;

		long eventTime = root.path("E").asLong();
		if (eventTime == 0) return null;

		JsonNode markPriceNode = root.get("p");
		if (markPriceNode == null) return null;
		BigDecimal markPrice = markPriceNode.decimalValue();

		String coin = context.getSymbolInverse(symbol);
		return new MarkPricePatch(coin, markPrice, Instant.ofEpochMilli(eventTime));
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		JsonNode b = root.get("b");
		JsonNode B = root.get("B");
		JsonNode a = root.get("a");
		JsonNode A = root.get("A");
		String symbol = root.path("s").asText();
		long eventTime = root.path("E").asLong();

		if (symbol.isEmpty()) return null;
		if (eventTime == 0) return null;

		BigDecimal bbPrice = b == null ? null : b.decimalValue();
		BigDecimal bbQty = B == null ? null : B.decimalValue();
		BigDecimal baPrice = a == null ? null : a.decimalValue();
		BigDecimal baQty = A == null ? null : A.decimalValue();

		if (bbPrice == null && bbQty == null && baPrice == null && baQty == null) return null;

		String coin = context.getSymbolInverse(symbol);
		return new BookTickerPatch(coin, bbPrice, bbQty, baPrice, baQty, Instant.ofEpochMilli(eventTime));
	}

	@Override
	public FundingRatePatch parseFundingRateMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseFundingRateInternal, root);
	}

	@Override
	public BookTickerPatch parseBookTickerMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseBookTickerInternal, root);
	}

	@Override
	public MarkPricePatch parseMarkPriceMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseMarkPriceInternal, root);
	}

	private <T> T parseErrorHandled(java.util.function.Function<JsonNode, T> parser, JsonNode root) {
		try {
			return parser.apply(root);
		} catch (IllegalArgumentException ex) {
			Logger.log(ex.getMessage());
			return null; // Not a BookTicker message
		} catch (Exception ex) {
			return null;
		}
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return null; // Binance ping/pong handled at WebSocket client level
	}
}
