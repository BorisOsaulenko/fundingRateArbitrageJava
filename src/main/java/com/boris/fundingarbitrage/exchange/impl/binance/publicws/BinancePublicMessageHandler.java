package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos.BookTickerMessage;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos.FundingRateMessage;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos.MarkPriceMessage;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public class BinancePublicMessageHandler extends PublicMessageHandler {
	private final ExchangeContext context;
	private final ObjectMapper jsonMapper = ObjectMapperSingleton.getInstance();

	public BinancePublicMessageHandler(
					ExchangeContext exchangeContext,
					PublicHttpClient publicHttpClient
	) {
		super(publicHttpClient);
		this.context = exchangeContext;
	}

	private FundingRatePatch parseFundingRateInternal(String message) throws JsonProcessingException {
		FundingRateMessage fundingRateMessage = jsonMapper.readValue(message, FundingRateMessage.class);
		String coin = context.getSymbolInverse(fundingRateMessage.s());

		return new FundingRatePatch(
						coin,
						Double.parseDouble(fundingRateMessage.r()),
						Instant.ofEpochMilli(fundingRateMessage.T()),
						Instant.ofEpochMilli(fundingRateMessage.E())
		);
	}

	private MarkPricePatch parseMarkPriceInternal(String message) throws JsonProcessingException {
		MarkPriceMessage markPriceMessage = jsonMapper.readValue(message, MarkPriceMessage.class);
		String coin = context.getSymbolInverse(markPriceMessage.s());
		return new MarkPricePatch(
						coin,
						Double.parseDouble(markPriceMessage.p()),
						Instant.ofEpochMilli(markPriceMessage.E())
		);
	}

	private BookTickerPatch parseBookTickerInternal(String message) throws JsonProcessingException {
		BookTickerMessage bookTickerMessage = jsonMapper.readValue(message, BookTickerMessage.class);
		PriceLevel bestBid = new PriceLevel(
						Double.parseDouble(bookTickerMessage.b()),
						Double.parseDouble(bookTickerMessage.B())
		);
		PriceLevel bestAsk = new PriceLevel(
						Double.parseDouble(bookTickerMessage.a()),
						Double.parseDouble(bookTickerMessage.A())
		);
		String coin = context.getSymbolInverse(bookTickerMessage.s());

		return new BookTickerPatch(coin, bestBid, bestAsk, Instant.ofEpochMilli(bookTickerMessage.E()));
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

	private <T> T parseErrorHandled(JsonParsingFunction<T> parser, String message) {
		try {
			return parser.apply(message);
		} catch (JsonParseException | JsonMappingException ex) {
			Logger.getInstance().log(ex.getMessage());
			return null; // Not a BookTicker message
		} catch (Exception ex) {
			return null;
		}
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return null; // Binance ping/pong handled at WebSocket client level
	}
}
