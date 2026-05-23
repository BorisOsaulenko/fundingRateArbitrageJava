package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
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
		String channel = root.path("arg").path("channel").asText();
		if (!"mark-price".equalsIgnoreCase(channel)) return null;
		String symbol = root.path("arg").path("instId").asText();
		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getFuturesSymbolInverse(symbol);
		String markPxNode = data.path("markPx").asText();
		BigDecimal markPx = markPxNode.isEmpty() ? null : new BigDecimal(markPxNode);

		long tsNode = data.path("ts").asLong();
		Instant ts = tsNode == 0 ? null : Instant.ofEpochMilli(tsNode);
		if (markPx == null || ts == null) return null;

		return new MarkPatch(coin, markPx, ts);
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root, Function<String, String> symbolInverse) {
		String channel = root.path("arg").path("channel").asText();
		if (!"tickers".equalsIgnoreCase(channel)) return null;
		String symbol = root.path("arg").path("instId").asText();
		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = symbolInverse.apply(symbol);
		String bidPxNode = data.path("bidPx").asText();
		String bidSzNode = data.path("bidSz").asText();
		String askPxNode = data.path("askPx").asText();
		String askSzNode = data.path("askSz").asText();
		BigDecimal bidPx = bidPxNode.isEmpty() ? null : new BigDecimal(bidPxNode);
		BigDecimal bidSz = bidSzNode.isEmpty() ? null : new BigDecimal(bidSzNode);
		BigDecimal askPx = askPxNode.isEmpty() ? null : new BigDecimal(askPxNode);
		BigDecimal askSz = askSzNode.isEmpty() ? null : new BigDecimal(askSzNode);
		if (bidPx == null && bidSz == null && askPx == null && askSz == null) return null;

		long tsNode = data.path("ts").asLong();
		Instant ts = tsNode == 0 ? null : Instant.ofEpochMilli(tsNode);
		if (ts == null) return null;


		return new BookTickerPatch(coin, bidPx, bidSz, askPx, askSz, ts);
	}

	public FundingPatch parseFundingRateMessageSymbol(JsonNode root) {
		return null;
	}

	public BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root) {
		return this.parseBookTickerInternal(root, context::getFuturesSymbolInverse);
	}

	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return this.parseBookTickerInternal(root, context::getSpotSymbolInverse);
	}
}
