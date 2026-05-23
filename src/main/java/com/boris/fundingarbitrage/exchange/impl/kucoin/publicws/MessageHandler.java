package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

@Slf4j
class MessageHandler {
	private static final String FUTURES_TICKER_TOPIC = "/contractMarket/tickerV2";
	private static final String INSTRUMENT_TOPIC = "/contract/instrument";
	private static final String SPOT_TICKER_TOPIC = "/spotMarket/level1";
	private final ExchangeContext context;

	public MessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private String symbolFromTopic(String topic) {
		int idx = topic.indexOf(':');
		if (idx <= 0 || idx + 1 >= topic.length()) return null;
		return topic.substring(idx + 1);
	}

	private BookTickerPatch parseFuturesBookTicker(
					JsonNode root,
					Function<String, String> symbolInverse,
					String expectedTopic
	) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(expectedTopic)) return null;
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

	public MarkPatch parseMarkPriceMessageSymbol(JsonNode root) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(INSTRUMENT_TOPIC)) return null;
		String subject = root.path("subject").asText();
		if (!"mark.index.price".equalsIgnoreCase(subject)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String markPriceNode = data.path("markPrice").asText();
		if (markPriceNode.isBlank()) return null;
		BigDecimal markPrice = new BigDecimal(markPriceNode);

		long ts = data.path("timestamp").asLong();
		if (ts == 0) return null;

		Instant timestamp = Instant.ofEpochMilli(ts);
		return new MarkPatch(context.getFuturesSymbolInverse(symbol), markPrice, timestamp);
	}

	public BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(FUTURES_TICKER_TOPIC)) return null;
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
		String coin = context.getFuturesSymbolInverse(symbol);

		return new BookTickerPatch(coin, bidPrice, bidSize, askPrice, askSize, timestamp);
	}

	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(SPOT_TICKER_TOPIC)) return null;

		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		// bids and asks are arrays: [ price, size ]
		JsonNode bids = data.path("bids");
		JsonNode asks = data.path("asks");

		String bidPriceNode = "";
		String bidSizeNode = "";
		String askPriceNode = "";
		String askSizeNode = "";

		if (bids != null && bids.isArray() && bids.size() >= 2) {
			bidPriceNode = bids.get(0).asText("");
			bidSizeNode = bids.get(1).asText("");
		}

		if (asks != null && asks.isArray() && asks.size() >= 2) {
			askPriceNode = asks.get(0).asText("");
			askSizeNode = asks.get(1).asText("");
		}

		BigDecimal bidPrice = bidPriceNode.isBlank() ? null : new BigDecimal(bidPriceNode);
		BigDecimal bidSize = bidSizeNode.isBlank() ? null : new BigDecimal(bidSizeNode);
		BigDecimal askPrice = askPriceNode.isBlank() ? null : new BigDecimal(askPriceNode);
		BigDecimal askSize = askSizeNode.isBlank() ? null : new BigDecimal(askSizeNode);

		// if all price/size fields are missing, ignore the message
		if (bidPrice == null && bidSize == null && askPrice == null && askSize == null) return null;

		long ts = data.path("timestamp").asLong();
		if (ts == 0L) return null;

		// sample timestamp is in milliseconds
		Instant timestamp = Instant.ofEpochMilli(ts);

		String coin = context.getSpotSymbolInverse(symbol);
		return new BookTickerPatch(coin, bidPrice, bidSize, askPrice, askSize, timestamp);
	}
}