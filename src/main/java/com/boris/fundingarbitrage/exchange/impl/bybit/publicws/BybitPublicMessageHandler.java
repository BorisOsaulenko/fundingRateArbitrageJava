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

		JsonNode markPriceNode = data.get("markPrice");
		BigDecimal markPrice = markPriceNode == null ? BigDecimal.ZERO : markPriceNode.decimalValue();
		if (markPrice.compareTo(BigDecimal.ZERO) == 0) return null;

		String coin = context.getSymbolInverse(symbol);
		return new MarkPricePatch(coin, markPrice, parseTimestamp(root));
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null) return null;
		String symbol = data.path("symbol").asText();
		if (symbol == null || symbol.isEmpty()) return null;
		String coin = context.getSymbolInverse(symbol);
		JsonNode bidPrNode = data.get("bid1Price");
		JsonNode bidSzNode = data.get("bid1Size");
		JsonNode askPrNode = data.get("ask1Price");
		JsonNode askSzNode = data.get("ask1Size");
		BigDecimal bidPr = bidPrNode == null ? BigDecimal.ZERO : bidPrNode.decimalValue();
		BigDecimal bidSz = bidSzNode == null ? BigDecimal.ZERO : bidSzNode.decimalValue();
		BigDecimal askPr = askPrNode == null ? BigDecimal.ZERO : askPrNode.decimalValue();
		BigDecimal askSz = askSzNode == null ? BigDecimal.ZERO : askSzNode.decimalValue();
		Instant timestamp = parseTimestamp(root);
		if (bidPr.compareTo(BigDecimal.ZERO) == 0 &&
				bidSz.compareTo(BigDecimal.ZERO) == 0 &&
				askPr.compareTo(BigDecimal.ZERO) == 0 &&
				askSz.compareTo(BigDecimal.ZERO) == 0) return null;
		return new BookTickerPatch(
						coin,
						bidPr.compareTo(BigDecimal.ZERO) == 0 ? null : bidPr,
						bidSz.compareTo(BigDecimal.ZERO) == 0 ? null : bidSz,
						askPr.compareTo(BigDecimal.ZERO) == 0 ? null : askPr,
						askSz.compareTo(BigDecimal.ZERO) == 0 ? null : askSz,
						timestamp
		);
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
		return null;
	}
}
