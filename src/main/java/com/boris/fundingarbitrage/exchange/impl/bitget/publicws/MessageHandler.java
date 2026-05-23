package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

class MessageHandler {
	private final ExchangeContext context;

	public MessageHandler(ExchangeContext context) {
		this.context = context;
	}

	public MarkPatch parseMarkPriceMessageSymbol(JsonNode root) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getFuturesSymbolInverse(symbol);
		String markPriceText = data.path("markPrice").asText();
		if (markPriceText.isBlank()) return null;
		BigDecimal markPrice = new BigDecimal(markPriceText);

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new MarkPatch(coin, markPrice, timestamp);
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root, Function<String, String> symbolInverse) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = symbolInverse.apply(symbol);
		String bidPrNode = data.get("bidPr").asText();
		String bidSzNode = data.get("bidSz").asText();
		String askPrNode = data.get("askPr").asText();
		String askSzNode = data.get("askSz").asText();
		BigDecimal bidPr = bidPrNode.isEmpty() ? null : new BigDecimal(bidPrNode);
		BigDecimal bidSz = bidSzNode.isEmpty() ? null : new BigDecimal(bidSzNode);
		BigDecimal askPr = askPrNode.isEmpty() ? null : new BigDecimal(askPrNode);
		BigDecimal askSz = askSzNode.isEmpty() ? null : new BigDecimal(askSzNode);
		if (bidPr == null && bidSz == null && askPr == null && askSz == null) return null;

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new BookTickerPatch(coin, bidPr, bidSz, askPr, askSz, timestamp);
	}

	public BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root) {
		return parseBookTickerInternal(root, context::getFuturesSymbolInverse);
	}

	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return parseBookTickerInternal(root, context::getSpotSymbolInverse);
	}
}