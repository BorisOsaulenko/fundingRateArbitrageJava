package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

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

class BitgetPublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;

	public BitgetPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
		String markPriceText = data.path("markPrice").asText();
		BigDecimal markPrice = markPriceText.isEmpty() ? null : new BigDecimal(markPriceText);
		if (markPrice == null) return null;

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new MarkPricePatch(coin, markPrice, timestamp);
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
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
	public String getResponseToPingMessage(String message) {
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		return null;
	}
}
