package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.IMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

class MessageHandler implements IMessageHandler {
	private final ExchangeContext context;

	public MessageHandler(ExchangeContext context) {
		this.context = context;
	}

	@Override
	public MarkPricePatch parseMarkPriceMessageSymbol(JsonNode root) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getFuturesSymbolInverse(symbol);
		String markPriceText = data.path("mark").asText();
		BigDecimal markPrice = markPriceText.isEmpty() ? null : new BigDecimal(markPriceText);
		if (markPrice == null) return null;

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new MarkPricePatch(coin, markPrice, timestamp);
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

	@Override
	public BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root) {
		return parseBookTickerInternal(root, context::getFuturesSymbolInverse);
	}

	@Override
	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return parseBookTickerInternal(root, context::getSpotSymbolInverse);
	}

	@Override
	public FundingRatePatch parseFundingRateMessageSymbol(JsonNode root) {
		return null; // Full funding via rest api
	}

	@Override
	public String getResponseToSpotPingMessage(String message) {
		return null;
	}

	@Override
	public String getResponseToFuturesPingMessage(String message) {
		return null;
	}
}
