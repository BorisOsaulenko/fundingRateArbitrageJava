package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

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

class BybitPublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;

	public BybitPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private Instant parseTimestamp(JsonNode root) {
		long ts = root.path("ts").asLong();
		if (ts == 0) throw new IllegalArgumentException("Missing or invalid timestamp in message: " + root);
		return Instant.ofEpochMilli(ts);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null) return null;

		String symbol = data.path("symbol").asText();
		if (symbol.isEmpty()) return null;

		String markPriceNode = data.path("markPrice").asText();
		BigDecimal markPrice = markPriceNode.isEmpty() ? null : new BigDecimal(markPriceNode);
		if (markPrice == null) return null;

		String coin = context.getFuturesSymbolInverse(symbol);
		return new MarkPricePatch(coin, markPrice, parseTimestamp(root));
	}

	//{
	// "topic":"orderbook.1.PUMPBTCUSDT",
	// "ts":1775295657176,
	// "type":"snapshot",
	// "data":{
	// 		"s":"PUMPBTCUSDT",
	// 		"b":[["0.0165","6430.7"]],
	// 		"a":[["0.01657","27308.5"]],
	// 		"u":2274751,"seq":153292640338
	// 		},
	// 	"cts":1775295657172
	// 	}
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
	public BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root) {
		return parseErrorHandled((json -> parseBookTickerInternal(json, context::getFuturesSymbolInverse)), root);
	}

	@Override
	public MarkPricePatch parseMarkPriceMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseMarkPriceInternal, root);
	}

	@Override
	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return parseErrorHandled((json -> parseBookTickerInternal(json, context::getSpotSymbolInverse)), root);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		return null;
	}
}
