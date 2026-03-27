package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

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

class KucoinPublicMessageHandler implements PublicMessageHandler {
	private static final String TICKER_TOPIC = "/contractMarket/tickerV2";
	private static final String INSTRUMENT_TOPIC = "/contract/instrument";
	private final ExchangeContext context;

	public KucoinPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private String symbolFromTopic(String topic) {
		int idx = topic.indexOf(':');
		if (idx <= 0 || idx + 1 >= topic.length()) return null;
		return topic.substring(idx + 1);
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
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
		String coin = context.getFuturesSymbolInverse(symbol);

		return new BookTickerPatch(coin, bidPrice, bidSize, askPrice, askSize, timestamp);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(INSTRUMENT_TOPIC)) return null;
		String subject = root.path("subject").asText();
		if (!"mark.index.price".equalsIgnoreCase(subject)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String markPriceNode = data.path("markPrice").asText();
		BigDecimal markPrice = markPriceNode.isEmpty() ? null : new BigDecimal(markPriceNode);
		if (markPrice == null) return null;

		long ts = data.path("timestamp").asLong();
		if (ts == 0) return null;

		Instant timestamp = Instant.ofEpochMilli(ts);
		return new MarkPricePatch(context.getFuturesSymbolInverse(symbol), markPrice, timestamp);
	}

	private FundingRatePatch parseFundingRateInternal(JsonNode root) {
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(INSTRUMENT_TOPIC)) return null;
		String subject = root.path("subject").asText();
		if (!"funding.rate".equalsIgnoreCase(subject)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String rateNode = data.path("fundingRate").asText();
		BigDecimal rate = rateNode.isEmpty() ? null : new BigDecimal(rateNode);
		if (rate == null) return null;

		long ts = data.path("timestamp").asLong();
		if (ts == 0) return null;

		Instant timestamp = Instant.ofEpochMilli(ts);
		return new FundingRatePatch(context.getFuturesSymbolInverse(symbol), rate, null, timestamp);
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
	public BookTickerPatch parseBookTickerMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseBookTickerInternal, root);
	}

	@Override
	public MarkPricePatch parseMarkPriceMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseMarkPriceInternal, root);
	}

	@Override
	public FundingRatePatch parseFundingRateMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseFundingRateInternal, root);
	}

	@Override
	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseBookTickerInternal, root);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return null; // Client sends ping, server does not expect any response
	}
}
