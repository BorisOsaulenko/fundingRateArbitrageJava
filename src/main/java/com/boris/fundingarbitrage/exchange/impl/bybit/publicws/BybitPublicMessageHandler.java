package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

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

public class BybitPublicMessageHandler extends PublicMessageHandler {
	private final ExchangeContext context;
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	public BybitPublicMessageHandler(ExchangeContext context, PublicHttpClient publicHttpClient) {
		super(publicHttpClient);
		this.context = context;
	}

	private boolean isNullOrWhiteSpace(String str) {
		return str == null || str.trim().isEmpty();
	}

	private JsonNode parseDataNode(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		JsonNode topic = root.get("topic");
		if (topic == null || !topic.asText().startsWith("tickers.")) return null;
		JsonNode data = root.get("data");
		if (data == null) return null;
		if (data.isArray() && !data.isEmpty()) return data.get(0);
		return data;
	}

	private String resolveSymbol(JsonNode dataNode) {
		return dataNode.path("symbol").asText();
	}

	private Instant parseTimestamp(JsonNode root, JsonNode data) {
		String ts = data.path("ts").asText();
		if (ts == null || ts.isEmpty()) ts = root.path("ts").asText();
		if (ts == null || ts.isEmpty()) return Instant.now();
		return Instant.ofEpochMilli(Long.parseLong(ts));
	}

	private FundingRatePatch parseFundingRateInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		JsonNode data = parseDataNode(message);
		if (data == null) return null;
		String symbol = resolveSymbol(data);
		String coin = context.getSymbolInverse(symbol);
		String fundingRate = data.path("fundingRate").asText();
		String nextFunding = data.path("nextFundingTime").asText();
		if ((fundingRate == null || fundingRate.isEmpty()) && (nextFunding == null || nextFunding.isEmpty())) {
			return null;
		}

		return new FundingRatePatch(
						coin,
						isNullOrWhiteSpace(fundingRate) ? null : Double.parseDouble(fundingRate),
						isNullOrWhiteSpace(nextFunding) ? null : Instant.ofEpochMilli(Long.parseLong(nextFunding)),
						parseTimestamp(root, data)
		);
	}

	private MarkPricePatch parseMarkPriceInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		JsonNode data = parseDataNode(message);
		if (data == null) return null;
		String symbol = resolveSymbol(data);
		String coin = context.getSymbolInverse(symbol);
		String markPrice = data.path("markPrice").asText();
		if (markPrice == null || markPrice.isEmpty()) return null;
		return new MarkPricePatch(coin, Double.parseDouble(markPrice), parseTimestamp(root, data));
	}

	private BookTickerPatch parseBookTickerInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		JsonNode data = parseDataNode(message);
		if (data == null) return null;
		String symbol = resolveSymbol(data);
		String coin = context.getSymbolInverse(symbol);
		String bidPr = data.path("bid1Price").asText();
		String bidSz = data.path("bid1Size").asText();
		String askPr = data.path("ask1Price").asText();
		String askSz = data.path("ask1Size").asText();
		if (bidPr == null || bidPr.isEmpty() || askPr == null || askPr.isEmpty()) return null;
		PriceLevel bestBid = new PriceLevel(Double.parseDouble(bidPr), Double.parseDouble(bidSz));
		PriceLevel bestAsk = new PriceLevel(Double.parseDouble(askPr), Double.parseDouble(askSz));
		return new BookTickerPatch(coin, bestBid, bestAsk, parseTimestamp(root, data));
	}

	private <T> T parseErrorHandled(JsonParsingFunction<T> parser, String message) {
		try {
			return parser.apply(message);
		} catch (JsonParseException | JsonMappingException ex) {
			Logger.getInstance().log(ex.getMessage());
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
