package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

class BitgetPublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;

	public BitgetPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private FundingRatePatch parseFundingRateInternal(JsonNode root) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
		JsonNode fundingRateNode = data.get("fundingRate");
		BigDecimal fundingRate = fundingRateNode == null ? BigDecimal.ZERO : fundingRateNode.decimalValue();
		long nextFundingTime = data.path("nextFundingTime").asLong();
		if (fundingRate.compareTo(BigDecimal.ZERO) == 0 && nextFundingTime == 0) return null;

		Instant settlement = Instant.ofEpochMilli(nextFundingTime);

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new FundingRatePatch(coin, fundingRate, settlement, timestamp);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
		JsonNode markPriceNode = data.get("markPrice");
		BigDecimal markPrice = markPriceNode == null ? BigDecimal.ZERO : markPriceNode.decimalValue();
		if (markPrice.compareTo(BigDecimal.ZERO) == 0) return null;

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new MarkPricePatch(coin, markPrice, timestamp);
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
		JsonNode bidPrNode = data.get("bidPr");
		JsonNode bidSzNode = data.get("bidSz");
		JsonNode askPrNode = data.get("askPr");
		JsonNode askSzNode = data.get("askSz");
		BigDecimal bidPr = bidPrNode == null ? BigDecimal.ZERO : bidPrNode.decimalValue();
		BigDecimal bidSz = bidSzNode == null ? BigDecimal.ZERO : bidSzNode.decimalValue();
		BigDecimal askPr = askPrNode == null ? BigDecimal.ZERO : askPrNode.decimalValue();
		BigDecimal askSz = askSzNode == null ? BigDecimal.ZERO : askSzNode.decimalValue();
		if (bidPr.compareTo(BigDecimal.ZERO) == 0 &&
				bidSz.compareTo(BigDecimal.ZERO) == 0 &&
				askPr.compareTo(BigDecimal.ZERO) == 0 &&
				askSz.compareTo(BigDecimal.ZERO) == 0) return null;

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new BookTickerPatch(
						coin,
						bidPr.compareTo(BigDecimal.ZERO) == 0 ? null : bidPr,
						bidSz.compareTo(BigDecimal.ZERO) == 0 ? null : bidSz,
						askPr.compareTo(BigDecimal.ZERO) == 0 ? null : askPr,
						askSz.compareTo(BigDecimal.ZERO) == 0 ? null : askSz,
						timestamp
		);
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
