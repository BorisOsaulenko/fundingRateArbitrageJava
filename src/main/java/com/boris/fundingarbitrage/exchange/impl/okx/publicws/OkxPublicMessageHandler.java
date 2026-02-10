package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class OkxPublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;

	public OkxPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private static Double parseDouble(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return null;
		String text = val.asText();
		if (text == null || text.isEmpty()) return null;
		return Double.parseDouble(text);
	}

	private static Instant parseInstantMillis(JsonNode node, String field) {
		JsonNode val = node.get(field);
		if (val == null || val.isNull()) return null;
		String text = val.asText();
		if (text == null || text.isEmpty()) return null;
		return Instant.ofEpochMilli(Long.parseLong(text));
	}

	private FundingRatePatch parseFundingRateInternal(JsonNode root) {
		String channel = root.path("arg").path("channel").asText();
		if (!"funding-rate".equalsIgnoreCase(channel)) return null;
		String symbol = root.path("arg").path("instId").asText();
		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
		Double rate = parseDouble(data, "fundingRate");
		Instant nextFunding = parseInstantMillis(data, "nextFundingTime");
		Instant ts = parseInstantMillis(data, "ts");
		if (rate == null || nextFunding == null || ts == null) return null;

		return new FundingRatePatch(coin, rate, nextFunding, ts);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String channel = root.path("arg").path("channel").asText();
		if (!"mark-price".equalsIgnoreCase(channel)) return null;
		String symbol = root.path("arg").path("instId").asText();
		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
		Double markPx = parseDouble(data, "markPx");
		Instant ts = parseInstantMillis(data, "ts");
		if (markPx == null || ts == null) return null;

		return new MarkPricePatch(coin, markPx, ts);
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		String channel = root.path("arg").path("channel").asText();
		if (!"tickers".equalsIgnoreCase(channel)) return null;
		String symbol = root.path("arg").path("instId").asText();
		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
		Double bidPx = parseDouble(data, "bidPx");
		Double bidSz = parseDouble(data, "bidSz");
		Double askPx = parseDouble(data, "askPx");
		Double askSz = parseDouble(data, "askSz");
		Instant ts = parseInstantMillis(data, "ts");
		if (ts == null) return null;
		if (bidPx == null && bidSz == null && askPx == null && askSz == null) return null;

		return new BookTickerPatch(coin, bidPx, bidSz, askPx, askSz, ts);
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
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		return null;
	}
}
