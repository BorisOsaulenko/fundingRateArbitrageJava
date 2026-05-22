package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

class MessageHandler implements IMessageHandler {
	private static final String TICKER_TOPIC = "/contractMarket/tickerV2";
	private static final String INSTRUMENT_TOPIC = "/contract/instrument";
	private final ExchangeContext context;

	public MessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private String symbolFromTopic(String topic) {
		int idx = topic.indexOf(':');
		if (idx <= 0 || idx + 1 >= topic.length()) return null;
		return topic.substring(idx + 1);
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root, Function<String, String> symbolInverse) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(TICKER_TOPIC)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String bidPriceNode = data.path("bestBidPrice").asText();
		String bidSizeNode = data.path("bestBidSize").asText();
		String askPriceNode = data.path("bestAskPrice").asText();
		String askSizeNode = data.path("bestAskSize").asText();
		BigDecimal bidPrice = bidPriceNode.isEmpty() ? null : new BigDecimal(bidPriceNode);
		BigDecimal bidSize = bidSizeNode.isEmpty() ? null : new BigDecimal(bidSizeNode);
		BigDecimal askPrice = askPriceNode.isEmpty() ? null : new BigDecimal(askPriceNode);
		BigDecimal askSize = askSizeNode.isEmpty() ? null : new BigDecimal(askSizeNode);
		if (bidPrice == null && bidSize == null && askSize == null && askPrice == null) return null;

		long ts = data.path("ts").asLong();
		if (ts == 0L) return null;

		Instant timestamp = Instant.ofEpochMilli(ts / 1000_000);
		String coin = symbolInverse.apply(symbol);

		return new BookTickerPatch(coin, bidPrice, bidSize, askPrice, askSize, timestamp);
	}

	@Override
	public MarkPatch parseMarkPriceMessageSymbol(JsonNode root) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(INSTRUMENT_TOPIC)) return null;
		String subject = root.path("subject").asText();
		if (!"mark.index.price".equalsIgnoreCase(subject)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String markPriceNode = data.path("mark").asText();
		BigDecimal markPrice = markPriceNode.isEmpty() ? null : new BigDecimal(markPriceNode);
		if (markPrice == null) return null;

		long ts = data.path("timestamp").asLong();
		if (ts == 0) return null;

		Instant timestamp = Instant.ofEpochMilli(ts);
		return new MarkPatch(context.getFuturesSymbolInverse(symbol), markPrice, timestamp);
	}

	@Override
	public FundingPatch parseFundingRateMessageSymbol(JsonNode root) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(INSTRUMENT_TOPIC)) return null;
		String subject = root.path("subject").asText();
		if (!"funding.rate".equalsIgnoreCase(subject)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String rateNode = data.path("funding").asText();
		BigDecimal rate = rateNode.isEmpty() ? null : new BigDecimal(rateNode);
		if (rate == null) return null;

		long ts = data.path("timestamp").asLong();
		if (ts == 0) return null;

		Instant timestamp = Instant.ofEpochMilli(ts);
		return new FundingPatch(context.getFuturesSymbolInverse(symbol), rate, null, timestamp);
	}

	@Override
	public BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root) {
		return this.parseBookTickerInternal(root, context::getFuturesSymbolInverse);
	}

	@Override
	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return this.parseBookTickerInternal(root, context::getSpotSymbolInverse);
	}
}
