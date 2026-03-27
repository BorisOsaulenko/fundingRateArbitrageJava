package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

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

class BinancePublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;

	public BinancePublicMessageHandler(ExchangeContext exchangeContext) {
		this.context = exchangeContext;
	}

	private FundingRatePatch parseFundingRateInternal(JsonNode root) {
		String symbol = root.path("s").asText();
		if (symbol.isEmpty()) return null;

		JsonNode rateNode = root.get("r");
		if (rateNode == null) return null;
		BigDecimal rate = new BigDecimal(rateNode.asText());

		long settlementTime = root.path("T").asLong();
		if (settlementTime == 0) return null;

		long eventTime = root.path("E").asLong();
		if (eventTime == 0) return null;

		String coin = context.getFuturesSymbolInverse(symbol);

		return new FundingRatePatch(coin, rate, Instant.ofEpochMilli(settlementTime), Instant.ofEpochMilli(eventTime));
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String symbol = root.path("s").asText();
		if (symbol.isEmpty()) return null;

		long eventTime = root.path("E").asLong();
		if (eventTime == 0) return null;

		String markPriceNode = root.path("p").asText();
		if (markPriceNode.isEmpty()) return null;
		BigDecimal markPrice = new BigDecimal(markPriceNode);

		String coin = context.getFuturesSymbolInverse(symbol);
		return new MarkPricePatch(coin, markPrice, Instant.ofEpochMilli(eventTime));
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		String bNode = root.path("b").asText();
		String BNode = root.path("B").asText();
		String aNode = root.path("a").asText();
		String ANode = root.path("A").asText();
		BigDecimal bbPrice = bNode.isEmpty() ? null : new BigDecimal(bNode);
		BigDecimal bbQty = BNode.isEmpty() ? null : new BigDecimal(BNode);
		BigDecimal baPrice = aNode.isEmpty() ? null : new BigDecimal(aNode);
		BigDecimal baQty = ANode.isEmpty() ? null : new BigDecimal(ANode);
		String symbol = root.path("s").asText();
		long eventTime = root.path("E").asLong();

		if (symbol.isEmpty()) return null;
		if (eventTime == 0) eventTime = Instant.now().toEpochMilli();
		if (bbPrice == null && bbQty == null && baPrice == null && baQty == null) return null;

		String coin = context.getFuturesSymbolInverse(symbol);
		return new BookTickerPatch(coin, bbPrice, bbQty, baPrice, baQty, Instant.ofEpochMilli(eventTime));
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
	public BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root) {
		return parseErrorHandled(this::parseBookTickerInternal, root);
	}

	private <T> T parseErrorHandled(Function<JsonNode, T> parser, JsonNode root) {
		try {
			return parser.apply(root);
		} catch (IllegalArgumentException ex) {
			Logger.log(ex.toString());
			return null;
		} catch (Exception ex) {
			return null;
		}
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return null; // Binance ping/pong handled at WebSocket client level
	}
}
