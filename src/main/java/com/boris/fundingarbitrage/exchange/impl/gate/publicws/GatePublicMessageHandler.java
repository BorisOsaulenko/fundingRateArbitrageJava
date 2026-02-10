package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class GatePublicMessageHandler implements PublicMessageHandler {
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

		double rate = entry.path("funding_rate").asDouble();
		if (rate == 0.0) return null;

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

		double mark = entry.path("mark_price").asDouble();
		if (mark == 0.0) return null;

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

		double bidPrice = result.path("b").asDouble();
		double bidSize = result.path("B").asDouble();
		double askPrice = result.path("a").asDouble();
		double askSize = result.path("A").asDouble();

		boolean nonePresent = bidPrice == 0.0 && bidSize == 0.0 && askPrice == 0.0 && askSize == 0.0;
		if (nonePresent) return null;

		long time = result.path("t").asLong();
		Instant ts = Instant.ofEpochMilli(time);
		return new BookTickerPatch(coin, bidPrice, bidSize, askPrice, askSize, ts);
	}

	private <T> T parseErrorHandled(java.util.function.Function<JsonNode, T> parser, JsonNode root) {
		try {
			return parser.apply(root);
		} catch (IllegalArgumentException ex) {
			return null;
		} catch (Exception ex) {
			Logger.error(ex.getMessage());
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
