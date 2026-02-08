package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class BybitPublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;

	public BybitPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private Instant parseTimestamp(JsonNode root) {
		long ts = root.path("ts").asLong();
		return Instant.ofEpochMilli(ts);
	}

	private FundingRatePatch parseFundingRateInternal(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null) return null;
		String symbol = data.path("symbol").asText();
		String coin = context.getSymbolInverse(symbol);
		String fundingRate = data.path("fundingRate").asText();
		String nextFunding = data.path("nextFundingTime").asText();

		boolean fundingProvided = fundingRate != null && !fundingRate.isEmpty();
		boolean nextFundingProvided = nextFunding != null && !nextFunding.isEmpty();
		if (!fundingProvided && !nextFundingProvided) {
			return null;
		}

		return new FundingRatePatch(
						coin,
						fundingProvided ? Double.parseDouble(fundingRate) : null,
						nextFundingProvided ? Instant.ofEpochMilli(Long.parseLong(nextFunding)) : null,
						parseTimestamp(root)
		);
	}

	private MarkPricePatch parseMarkPriceInternal(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null) return null;

		String symbol = data.path("symbol").asText();
		if (symbol.isEmpty()) return null;

		double markPrice = data.path("markPrice").asDouble();
		if (markPrice == 0.0) return null;

		String coin = context.getSymbolInverse(symbol);
		return new MarkPricePatch(coin, markPrice, parseTimestamp(root));
	}

	private BookTickerPatch parseBookTickerInternal(JsonNode root) {
		JsonNode data = root.get("data");
		if (data == null) return null;
		String symbol = data.path("symbol").asText();
		String coin = context.getSymbolInverse(symbol);
		double bidPr = data.path("bid1Price").asDouble();
		double bidSz = data.path("bid1Size").asDouble();
		double askPr = data.path("ask1Price").asDouble();
		double askSz = data.path("ask1Size").asDouble();
		if (bidPr == 0.0 && bidSz == 0.0 && askPr == 0.0 && askSz == 0.0) return null;
		return new BookTickerPatch(
						coin,
						bidPr == 0.0 ? null : bidPr,
						bidSz == 0.0 ? null : bidSz,
						askPr == 0.0 ? null : askPr,
						askSz == 0.0 ? null : askSz,
						parseTimestamp(root)
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
