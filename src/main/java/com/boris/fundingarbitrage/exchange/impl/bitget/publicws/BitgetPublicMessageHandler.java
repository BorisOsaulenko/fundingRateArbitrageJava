package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.contract.PriceLevel;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.JsonParsingFunction;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public class BitgetPublicMessageHandler extends PublicMessageHandler {
	private final ExchangeContext context;
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public BitgetPublicMessageHandler(ExchangeContext context, PublicHttpClient publicHttpClient) {
		super(publicHttpClient);
		this.context = context;
	}

	private JsonNode parseDataNode(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		if (!root.has("arg") || !root.has("data")) return null;
		JsonNode arg = root.get("arg");
		String channel = arg.path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;
		JsonNode data = root.get("data");
		if (!data.isArray() || data.isEmpty()) return null;
		return data.get(0);
	}

	private Instant parseTimestamp(JsonNode dataNode) {
		String ts = dataNode.path("ts").asText();
		return Instant.ofEpochMilli(Long.parseLong(ts));
	}

	private FundingRatePatch parseFundingRateInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode data = parseDataNode(message);
		if (data == null) return null;

		String coin = context.getSymbolInverse(symbol);
		String fundingRateStr = data.path("fundingRate").asText();
		String nextFundingTime = data.path("nextFundingTime").asText();
		if (fundingRateStr == null || fundingRateStr.isEmpty()) return null;
		if (nextFundingTime == null || nextFundingTime.isEmpty()) return null;

		return new FundingRatePatch(
						coin,
						Double.parseDouble(fundingRateStr),
						Instant.ofEpochMilli(Long.parseLong(nextFundingTime)),
						parseTimestamp(data)
		);
	}

	private MarkPricePatch parseMarkPriceInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode data = parseDataNode(message);
		if (data == null) return null;

		String coin = context.getSymbolInverse(symbol);
		String markPrice = data.path("markPrice").asText();
		if (markPrice == null || markPrice.isEmpty()) return null;
		return new MarkPricePatch(coin, Double.parseDouble(markPrice), parseTimestamp(data));
	}

	private BookTickerPatch parseBookTickerInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String symbol = root.get("arg").path("instId").asText();
		String channel = root.get("arg").path("channel").asText();
		if (!"ticker".equalsIgnoreCase(channel)) return null;

		JsonNode data = parseDataNode(message);
		if (data == null) return null;

		String coin = context.getSymbolInverse(symbol);
		String bidPr = data.path("bidPr").asText();
		String bidSz = data.path("bidSz").asText();
		String askPr = data.path("askPr").asText();
		String askSz = data.path("askSz").asText();
		if (bidPr == null || bidPr.isEmpty() || askPr == null || askPr.isEmpty()) return null;

		PriceLevel bestBid = new PriceLevel(Double.parseDouble(bidPr), Double.parseDouble(bidSz));
		PriceLevel bestAsk = new PriceLevel(Double.parseDouble(askPr), Double.parseDouble(askSz));
		return new BookTickerPatch(coin, bestBid, bestAsk, parseTimestamp(data));
	}

	private <T> T parseErrorHandled(JsonParsingFunction<T> parser, String message) {
		try {
			return parser.apply(message);
		} catch (JsonParseException | JsonMappingException ex) {
			Logger.log(ex.getMessage());
			return null;
		} catch (Exception ex) {
			return null;
		}
	}

	@Override
	public FundingRatePatch parseFundingRateMessageSymbol(String message) {
		return parseErrorHandled(this::parseFundingRateInternal, message);
	}

	@Override
	public BookTickerPatch parseBookTickerMessageSymbol(String message) {
		return parseErrorHandled(this::parseBookTickerInternal, message);
	}

	@Override
	public MarkPricePatch parseMarkPriceMessageSymbol(String message) {
		return parseErrorHandled(this::parseMarkPriceInternal, message);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		if (message == null) return null;
		String trimmed = message.trim();
		if ("ping".equalsIgnoreCase(trimmed)) return "pong";
		return null;
	}
}
