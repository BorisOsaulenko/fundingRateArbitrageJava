package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

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

	public MessageHandler(ExchangeContext exchangeContext) {
		this.context = exchangeContext;
	}

	@Override
	public FundingPatch parseFundingRateMessageSymbol(JsonNode root) {
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

		return new FundingPatch(coin, rate, Instant.ofEpochMilli(settlementTime), Instant.ofEpochMilli(eventTime));
	}

	@Override
	public MarkPatch parseMarkPriceMessageSymbol(JsonNode root) {
		String symbol = root.path("s").asText();
		if (symbol.isEmpty()) return null;

		long eventTime = root.path("E").asLong();
		if (eventTime == 0) return null;

		String markPriceNode = root.path("p").asText();
		if (markPriceNode.isEmpty()) return null;
		BigDecimal markPrice = new BigDecimal(markPriceNode);

		String coin = context.getFuturesSymbolInverse(symbol);
		return new MarkPatch(coin, markPrice, Instant.ofEpochMilli(eventTime));
	}

	public BookTickerPatch parseBookTickerInternal(JsonNode root, Function<String, String> symbolInverse) {
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

		String coin = symbolInverse.apply(symbol);
		return new BookTickerPatch(coin, bbPrice, bbQty, baPrice, baQty, Instant.ofEpochMilli(eventTime));
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
