package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
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
import java.util.Arrays;

public class GatePublicMessageHandler implements PublicMessageHandler {
	private final ExchangeContext context;
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public GatePublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private boolean correctString(String s) {
		return s != null && !s.isEmpty();
	}

	private FundingRatePatch parseFundingRateInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
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
		String fundingRateStr = entry.path("funding_rate").asText();
		if (fundingRateStr == null || fundingRateStr.isEmpty()) return null;

		return new FundingRatePatch(
						coin, Double.parseDouble(fundingRateStr), null, // Gate provides no funding settlement updates via websocket
						Instant.ofEpochSecond(time)
		);
	}

	private MarkPricePatch parseMarkPriceInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
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

		String mark = entry.path("mark_price").asText();
		if (mark == null || mark.isEmpty()) return null;

		String coin = context.getSymbolInverse(symbol);
		return new MarkPricePatch(coin, Double.parseDouble(mark), Instant.ofEpochSecond(time));
	}

	private BookTickerPatch parseBookTickerInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String channel = root.path("channel").asText();
		String event = root.path("event").asText();
		if (!"futures.book_ticker".equalsIgnoreCase(channel) || !"update".equalsIgnoreCase(event)) {
			return null;
		}
		JsonNode result = root.get("result");
		if (result == null || !result.isObject()) return null;

		String symbol = result.path("s").asText();
		if (symbol == null || symbol.isEmpty()) return null;

		String bidPrice = result.path("b").asText();
		String bidSize = result.path("B").asText();
		String askPrice = result.path("a").asText();
		String askSize = result.path("A").asText();
		if (Arrays.stream((new String[]{bidPrice, bidSize, askSize, askPrice})).anyMatch(this::correctString)) {
			PriceLevel bid = new PriceLevel(Double.parseDouble(bidPrice), Double.parseDouble(bidSize));
			PriceLevel ask = new PriceLevel(Double.parseDouble(askPrice), Double.parseDouble(askSize));
			long time = result.path("t").asLong();
			Instant ts = Instant.ofEpochMilli(time);
			return new BookTickerPatch(context.getSymbolInverse(symbol), bid, ask, ts);
		}

		return null;
	}

	private <T> T parseErrorHandled(JsonParsingFunction<T> parser, String message) {
		try {
			return parser.apply(message);
		} catch (JsonParseException | JsonMappingException ex) {
			return null;
		} catch (Exception ex) {
			Logger.error(ex.getMessage());
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
