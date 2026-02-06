package com.boris.fundingarbitrage.exchange.impl.binance.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.impl.binance.privatews.pojos.DepositMessage;
import com.boris.fundingarbitrage.exchange.impl.binance.privatews.pojos.PartialFillMessage;
import com.boris.fundingarbitrage.exchange.privatews.PrivateMessageHandler;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.boris.fundingarbitrage.util.JsonParsingFunction;
import com.boris.fundingarbitrage.util.logger.Logger;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public class BinancePrivateMessageHandler implements PrivateMessageHandler {
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private final JsonParsingFunction<DepositPatch> parseDepositInternal = (message) -> {
		DepositMessage depositMessage = mapper.readValue(message, DepositMessage.class);
		var eventData = depositMessage.event();
		for (var balance : eventData.B()) {
			if (!"USDT".equals(balance.a())) continue;
			double free = Double.parseDouble(balance.f());
			if (free > 0) return new DepositPatch(free, Instant.ofEpochMilli(eventData.E()));
		}

		return null;
	};
	private final JsonParsingFunction<PartialFill> parsePartialFillInternal = (message) -> {
		PartialFillMessage partiallFillMessage = mapper.readValue(message, PartialFillMessage.class);
		var eventData = partiallFillMessage.event();
		if (!"USDT".equals(eventData.s())) return null;
		return new PartialFill(
						String.valueOf(eventData.i()),
						eventData.s(),
						Double.parseDouble(eventData.l()),
						Double.parseDouble(eventData.L()),
						Double.parseDouble(eventData.n()),
						Instant.ofEpochMilli(eventData.E())
		);
	};

	private <T> T parseErrorHandled(JsonParsingFunction<T> parser, String message) {
		try {
			return parser.apply(message);
		} catch (JsonParseException | JsonMappingException ex) {
			Logger.log(ex.getMessage());
			return null; // Not a BookTicker message
		} catch (Exception ex) {
			return null;
		}
	}

	@Override
	public DepositPatch parseDepositMessageSymbol(String message) {
		return parseErrorHandled(parseDepositInternal, message);
	}

	@Override
	public PartialFill parsePartialFillMessageSymbol(String message) {
		return parseErrorHandled(parsePartialFillInternal, message);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return null;
	}
}
