package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

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

	@Override
	public FundingPatch parseFundingRateMessageSymbol(JsonNode root) {
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		long time = root.path("time").asLong();

		if (!"futures.tickers".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) {
			return null;
		}

		JsonNode result = root.get("result");
		if (result == null) return null;

		JsonNode entry = result.get(0);

		String symbol = entry.path("contract").asText();
		if (symbol == null || symbol.isEmpty()) return null;
		String coin = context.getFuturesSymbolInverse(symbol);

		String rateNode = entry.get("funding_rate").asText();
		BigDecimal rate = rateNode.isEmpty() ? null : new BigDecimal(rateNode);
		if (rate == null) return null;

		return new FundingPatch(coin, rate, null, Instant.ofEpochSecond(time));
	}

	@Override
	public MarkPatch parseMarkPriceMessageSymbol(JsonNode root) {
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		long time = root.path("time").asLong();
		if (!"futures.tickers".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) {
			return null;
		}
		JsonNode result = root.get("result");
		if (result == null || !result.isArray()) return null;

		JsonNode entry = result.get(0);
		String symbol = entry.path("contract").asText();
		if (symbol == null || symbol.isEmpty()) return null;
		String coin = context.getFuturesSymbolInverse(symbol);

		String markNode = entry.path("mark_price").asText();
		BigDecimal mark = markNode.isEmpty() ? null : new BigDecimal(markNode);
		if (mark == null) return null;

		return new MarkPatch(coin, mark, Instant.ofEpochSecond(time));
	}

	public BookTickerPatch parseBookTickerInternal(JsonNode root, Function<String, String> symbolInverse) {
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		if (!"futures.book_ticker".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) return null;

		JsonNode result = root.get("result");
		if (result == null || !result.isObject()) return null;

		String symbol = result.path("s").asText();
		if (symbol == null || symbol.isEmpty()) return null;
		String coin = symbolInverse.apply(symbol);

		String bidPriceNode = result.path("b").asText();
		String bidSizeNode = result.path("B").asText();
		String askPriceNode = result.path("a").asText();
		String askSizeNode = result.path("A").asText();
		BigDecimal bidPrice = bidPriceNode.isEmpty() ? null : new BigDecimal(bidPriceNode);
		BigDecimal bidSize = bidSizeNode.isEmpty() ? null : new BigDecimal(bidSizeNode);
		BigDecimal askPrice = askPriceNode.isEmpty() ? null : new BigDecimal(askPriceNode);
		BigDecimal askSize = askSizeNode.isEmpty() ? null : new BigDecimal(askSizeNode);

		if (bidPrice == null && bidSize == null && askPrice == null && askSize == null) return null;

		long time = result.path("t").asLong();
		Instant ts = Instant.ofEpochMilli(time);
		return new BookTickerPatch(coin, bidPrice, bidSize, askPrice, askSize, ts);
	}

	@Override
	public BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root) {
		return parseBookTickerInternal(root, context::getFuturesSymbolInverse);
	}

	@Override
	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return parseBookTickerInternal(root, context::getSpotSymbolInverse);
	}
}
