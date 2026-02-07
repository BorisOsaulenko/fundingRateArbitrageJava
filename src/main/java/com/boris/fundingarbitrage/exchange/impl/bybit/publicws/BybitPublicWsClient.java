package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;

import java.net.URI;
import java.util.Arrays;

public class BybitPublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://stream.bybit.com/v5/public/linear");

	public BybitPublicWsClient(
					ExchangeContext context,
					BybitPublicMessageHandler messageHandler,
					BybitPublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String getSubscribeFrame(String[] symbols) {
		String[] topics = Arrays.stream(symbols).map(symbol -> "tickers." + symbol).toArray(String[]::new);
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeFrame(String[] symbols) {
		String[] topics = Arrays.stream(symbols).map(symbol -> "tickers." + symbol).toArray(String[]::new);
		return new WsRequest("unsubscribe", topics).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(String[] symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(String[] symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	protected String getSubscribeBookTickerFrame(String[] symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(String[] symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(String[] symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(String[] symbols) {
		return getUnsubscribeFrame(symbols);
	}
}
