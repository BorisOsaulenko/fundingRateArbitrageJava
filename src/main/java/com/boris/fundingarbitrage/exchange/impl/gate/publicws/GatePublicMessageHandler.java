package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

class GatePublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;

	public GatePublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private boolean correctString(String s) {
		return s != null && !s.isEmpty();
	}

	private FundingRatePatch parseFundingRateInternal(JsonNode root) {
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
		String coin = context.getSymbolInverse(symbol);

		String rateNode = entry.get("funding_rate").asText();
		BigDecimal rate = rateNode.isEmpty() ? null : new BigDecimal(rateNode);
		if (rate == null) return null;

		return new FundingRatePatch(coin, rate, null, Instant.ofEpochSecond(time));
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
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
		String coin = context.getSymbolInverse(symbol);

		String markNode = entry.path("mark_price").asText();
		BigDecimal mark = markNode.isEmpty() ? null : new BigDecimal(markNode);
		if (mark == null) return null;

		return new MarkPricePatch(coin, mark, Instant.ofEpochSecond(time));
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		if (!"futures.book_ticker".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) return null;

		JsonNode result = root.get("result");
		if (result == null || !result.isObject()) return null;

		String symbol = result.path("s").asText();
		if (symbol == null || symbol.isEmpty()) return null;
		String coin = context.getSymbolInverse(symbol);

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
		return parseErrorHandled(this::parseFundingRateInternal, root);
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
		return null;
	}
}
