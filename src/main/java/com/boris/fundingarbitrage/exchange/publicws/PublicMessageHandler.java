package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;

public interface PublicMessageHandler {
	FundingRatePatch parseFundingRateMessageSymbol(String message);

	BookTickerPatch parseBookTickerMessageSymbol(String message);

	MarkPricePatch parseMarkPriceMessageSymbol(String message);

	String getResponseToPingMessage(String message);
}
