package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.fasterxml.jackson.databind.JsonNode;

public interface PublicMessageHandler {
	FundingRatePatch parseFundingRateMessageSymbol(JsonNode root);

	BookTickerPatch parseFuturesBookTickerMessageSymbol(JsonNode root);

	MarkPricePatch parseMarkPriceMessageSymbol(JsonNode root);

	BookTickerPatch parseSpotBookTickerMessageSymbol(JsonNode root);

	String getResponseToPingMessage(String message);
}
