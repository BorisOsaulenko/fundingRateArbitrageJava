package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;

public abstract class PublicMessageHandler {
	protected final PublicHttpClient publicHttpClient; // Some exchanges have incomplete WS data and need HTTP client to fill the gaps

	public PublicMessageHandler(PublicHttpClient publicHttpClient) {
		this.publicHttpClient = publicHttpClient;
	}

	public abstract FundingRatePatch parseFundingRateMessageSymbol(String message);

	public abstract BookTickerPatch parseBookTickerMessageSymbol(String message);

	public abstract MarkPricePatch parseMarkPriceMessageSymbol(String message);

	public abstract String getResponseToPingMessage(String message);
}
