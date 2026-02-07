package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.PriceLevel;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.JsonParsingFunction;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.json.Json;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class KucoinPublicMessageHandler implements PublicMessageHandler {
	private static final String TICKER_TOPIC = "/contractMarket/tickerV2";
	private static final String INSTRUMENT_TOPIC = "/contract/instrument";
	private final ExchangeContext context;
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private final CoinVector<Boolean> updatingFundingRateVector = new CoinVector<>();
	private final CoinVector<Instant> settlementVector = new CoinVector<>();
	private final CoinVector<CompletableFuture<FundingRate>> fundingRateFutureVector = new CoinVector<>();

	public KucoinPublicMessageHandler(ExchangeContext context) {
		this.context = context;
	}

	private String symbolFromTopic(String topic) {
		int idx = topic.indexOf(':');
		if (idx <= 0 || idx + 1 >= topic.length()) return null;
		return topic.substring(idx + 1);
	}

	private BookTickerPatch parseBookTickerInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(TICKER_TOPIC)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String bidPriceText = data.path("bestBidPrice").asText();
		String bidSizeText = data.path("bestBidSize").asText();
		String askPriceText = data.path("bestAskPrice").asText();
		String askSizeText = data.path("bestAskSize").asText();
		String tsText = data.path("ts").asText();
		if (bidPriceText == null || bidPriceText.isEmpty()) return null;
		if (bidSizeText == null || bidSizeText.isEmpty()) return null;
		if (askPriceText == null || askPriceText.isEmpty()) return null;
		if (askSizeText == null || askSizeText.isEmpty()) return null;
		if (tsText == null || tsText.isEmpty()) return null;

		double bidPrice = Double.parseDouble(bidPriceText);
		double bidSize = Double.parseDouble(bidSizeText);
		double askPrice = Double.parseDouble(askPriceText);
		double askSize = Double.parseDouble(askSizeText);
		long ts = Long.parseLong(tsText);
		Instant timestamp = Json.toInstantMillisOrNanos(ts);
		String coin = context.getSymbolInverse(symbol);

		return new BookTickerPatch(coin, new PriceLevel(bidPrice, bidSize), new PriceLevel(askPrice, askSize), timestamp);
	}

	private MarkPricePatch parseMarkPriceInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(INSTRUMENT_TOPIC)) return null;
		String subject = root.path("subject").asText();
		if (!"mark.index.price".equalsIgnoreCase(subject)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String markPriceText = data.path("markPrice").asText();
		String timestampText = data.path("timestamp").asText();
		if (markPriceText == null || markPriceText.isEmpty()) return null;
		if (timestampText == null || timestampText.isEmpty()) return null;

		double markPrice = Double.parseDouble(markPriceText);
		long ts = Long.parseLong(timestampText);
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new MarkPricePatch(context.getSymbolInverse(symbol), markPrice, timestamp);
	}

	private FundingRatePatch parseFundingRateInternal(String message) throws JsonProcessingException {
		JsonNode root = mapper.readTree(message);
		String topic = root.path("topic").asText();
		if (topic == null || !topic.startsWith(INSTRUMENT_TOPIC)) return null;
		String subject = root.path("subject").asText();
		if (!"funding.rate".equalsIgnoreCase(subject)) return null;
		JsonNode data = root.get("data");
		if (data == null || !data.isObject()) return null;

		String symbol = symbolFromTopic(topic);
		if (symbol == null || symbol.isEmpty()) return null;

		String fundingRateText = data.path("fundingRate").asText();
		String timestampText = data.path("timestamp").asText();
		if (fundingRateText == null || fundingRateText.isEmpty()) return null;
		if (timestampText == null || timestampText.isEmpty()) return null;

		double rate = Double.parseDouble(fundingRateText);
		long ts = Long.parseLong(timestampText);
		Instant timestamp = Instant.ofEpochMilli(ts);

		return new FundingRatePatch(context.getSymbolInverse(symbol), rate, null, timestamp);
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
	public BookTickerPatch parseBookTickerMessageSymbol(String message) {
		return parseErrorHandled(this::parseBookTickerInternal, message);
	}

	@Override
	public MarkPricePatch parseMarkPriceMessageSymbol(String message) {
		return parseErrorHandled(this::parseMarkPriceInternal, message);
	}

	@Override
	public FundingRatePatch parseFundingRateMessageSymbol(String message) {
		return parseErrorHandled(this::parseFundingRateInternal, message);
	}


	@Override
	public String getResponseToPingMessage(String message) {
		return null; // Client sends ping, server does not expect any response
	}
}
