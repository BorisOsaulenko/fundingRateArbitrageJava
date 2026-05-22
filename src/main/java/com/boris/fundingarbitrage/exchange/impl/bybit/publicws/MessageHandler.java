package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

class MessageHandler implements IMessageHandler {
	private final ExchangeContext context;

	public MessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private Instant parseTimestamp(JsonNode root) {
		long ts = root.path("ts").asLong();
		if (ts == 0) throw new IllegalArgumentException("Missing or invalid timestamp in message: " + root);
		return Instant.ofEpochMilli(ts);
	}

	@Override
	public MarkPatch parseMarkPriceMessageSymbol(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null) return null;

		String symbol = data.path("symbol").asText();
		if (symbol.isEmpty()) return null;

		String markPriceNode = data.path("mark").asText();
		BigDecimal markPrice = markPriceNode.isEmpty() ? null : new BigDecimal(markPriceNode);
		if (markPrice == null) return null;

		String coin = context.getFuturesSymbolInverse(symbol);
		return new MarkPatch(coin, markPrice, parseTimestamp(root));
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root, Function<String, String> symbolInverse) {
		JsonNode data = root.get("data");
		if (data == null) return null;

		long ts = root.path("ts").asLong();
		if (ts == 0) return null;

		String symbol = data.path("s").asText();
		if (symbol.isEmpty()) return null;

		String coin = symbolInverse.apply(symbol);

		JsonNode bids = data.path("b");
		if (!bids.isArray()) return null;
		JsonNode bid1 = bids.get(0);
		BigDecimal bid1Size = new BigDecimal(bid1.get(1).asText());
		BigDecimal bid1Price = new BigDecimal(bid1.get(0).asText());

		JsonNode asks = data.path("a");
		if (!asks.isArray()) return null;
		JsonNode ask1 = asks.get(0);
		BigDecimal ask1Size = new BigDecimal(ask1.get(1).asText());
		BigDecimal ask1Price = new BigDecimal(ask1.get(0).asText());

		return new BookTickerPatch(
						coin,
						bid1Price,
						bid1Size,
						ask1Price,
						ask1Size,
						Instant.ofEpochMilli(ts)
		);
	}

	@Override
	public FundingPatch parseFundingRateMessageSymbol(JsonNode root) {
		return null;
	} // Full funding via rest api

	@Override
	public BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root) {
		return parseBookTickerInternal(root, context::getFuturesSymbolInverse);
	}

	@Override
	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return parseBookTickerInternal(root, context::getSpotSymbolInverse);
	}
}
