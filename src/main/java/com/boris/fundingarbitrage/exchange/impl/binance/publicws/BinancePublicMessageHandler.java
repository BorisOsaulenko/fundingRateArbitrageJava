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

import java.time.Instant;

public class BinancePublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;
	private final ObjectMapper jsonMapper = ObjectMapperSingleton.getInstance();

	public BinancePublicMessageHandler(ExchangeContext exchangeContext) {
		this.context = exchangeContext;
	}

	private FundingRatePatch parseFundingRateInternal(JsonNode root) {
		String symbol = root.path("s").asText();
		if (symbol.isEmpty()) return null;

		String rate = root.path("r").asText();
		if (rate.isEmpty()) return null;

		long settlementTime = root.path("T").asLong();
		if (settlementTime == 0) return null;

		long eventTime = root.path("E").asLong();
		if (eventTime == 0) return null;

		String coin = context.getSymbolInverse(symbol);

		return new FundingRatePatch(
						coin,
						Double.parseDouble(rate),
						Instant.ofEpochMilli(settlementTime),
						Instant.ofEpochMilli(eventTime)
		);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String symbol = root.path("s").asText();
		if (symbol.isEmpty()) return null;

		long eventTime = root.path("E").asLong();
		if (eventTime == 0) return null;

		String markPrice = root.path("p").asText();
		if (markPrice.isEmpty()) return null;

		String coin = context.getSymbolInverse(symbol);
		return new MarkPricePatch(coin, Double.parseDouble(markPrice), Instant.ofEpochMilli(eventTime));
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		String b = root.path("b").asText();
		String B = root.path("B").asText();
		String a = root.path("a").asText();
		String A = root.path("A").asText();
		String symbol = root.path("s").asText();
		long eventTime = root.path("E").asLong();

		if (symbol.isEmpty()) return null;
		if (eventTime == 0) return null;

		Double bbPrice = b.isEmpty() ? null : Double.parseDouble(b);
		Double bbQty = B.isEmpty() ? null : Double.parseDouble(B);
		Double baPrice = a.isEmpty() ? null : Double.parseDouble(a);
		Double baQty = A.isEmpty() ? null : Double.parseDouble(A);

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
