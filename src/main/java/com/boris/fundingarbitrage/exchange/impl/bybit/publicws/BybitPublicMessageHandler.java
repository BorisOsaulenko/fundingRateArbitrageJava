package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

class BybitPublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;

	public BybitPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private Instant parseTimestamp(JsonNode root) {
		long ts = root.path("ts").asLong();
		if (ts == 0) throw new IllegalArgumentException("Missing or invalid timestamp in message: " + root);
		return Instant.ofEpochMilli(ts);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null) return null;

		String symbol = data.path("symbol").asText();
		if (symbol.isEmpty()) return null;

		String markPriceNode = data.path("markPrice").asText();
		BigDecimal markPrice = markPriceNode.isEmpty() ? null : new BigDecimal(markPriceNode);
		if (markPrice == null) return null;

		String coin = context.getFuturesSymbolInverse(symbol);
		return new MarkPricePatch(coin, markPrice, parseTimestamp(root));
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null) return null;

		String symbol = data.path("symbol").asText();
		if (symbol.isEmpty()) return null;

		String coin = context.getFuturesSymbolInverse(symbol);
		String bidPrNode = data.path("bid1Price").asText();
		String bidSzNode = data.path("bid1Size").asText();
		String askPrNode = data.path("ask1Price").asText();
		String askSzNode = data.path("ask1Size").asText();
		BigDecimal bidPr = bidPrNode.isEmpty() ? null : new BigDecimal(bidPrNode);
		BigDecimal bidSz = bidSzNode.isEmpty() ? null : new BigDecimal(bidSzNode);
		BigDecimal askPr = askPrNode.isEmpty() ? null : new BigDecimal(askPrNode);
		BigDecimal askSz = askSzNode.isEmpty() ? null : new BigDecimal(askSzNode);

		Instant timestamp = parseTimestamp(root);
		if (bidPr == null && bidSz == null && askPr == null && askSz == null) return null;
		return new BookTickerPatch(coin, bidPr, bidSz, askPr, askSz, timestamp);
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
		return null;
	}
}
