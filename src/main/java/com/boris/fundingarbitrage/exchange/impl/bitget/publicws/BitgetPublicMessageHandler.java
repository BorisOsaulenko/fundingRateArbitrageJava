package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class BitgetPublicMessageHandler implements PublicMessageHandler {
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
		double fundingRateStr = data.path("fundingRate").asDouble();
		long nextFundingTime = data.path("nextFundingTime").asLong();
		if (fundingRateStr == 0.0 && nextFundingTime == 0) return null;

		Instant settlement = Instant.ofEpochMilli(nextFundingTime);

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new FundingRatePatch(coin, fundingRateStr, settlement, timestamp);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode dataArray = root.get("data");
		if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) return null;
		JsonNode data = dataArray.get(0);

		String coin = context.getSymbolInverse(symbol);
		double markPrice = data.path("markPrice").asDouble();
		if (markPrice == 0.0) return null;

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
		double bidPr = data.path("bidPr").asDouble();
		double bidSz = data.path("bidSz").asDouble();
		double askPr = data.path("askPr").asDouble();
		double askSz = data.path("askSz").asDouble();
		if (bidPr == 0.0 && bidSz == 0.0 && askPr == 0.0 && askSz == 0.0) return null;

		long ts = data.path("ts").asLong();
		if (ts == 0) return null;
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new BookTickerPatch(
						coin,
						bidPr == 0.0 ? null : bidPr,
						bidSz == 0.0 ? null : bidSz,
						askPr == 0.0 ? null : askPr,
						askSz == 0.0 ? null : askSz,
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
