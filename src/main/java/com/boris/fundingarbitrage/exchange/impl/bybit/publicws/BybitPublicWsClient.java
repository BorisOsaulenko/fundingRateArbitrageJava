package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.List;

public class BybitPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://stream.bybit.com/v5/public/linear");

	public BybitPublicWsClient(
					ExchangeContext context,
					BybitPublicMessageHandler messageHandler,
					BybitPublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String getSubscribeFrame(List<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeFrame(List<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("unsubscribe", topics).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(List<String> symbols) {
		return null;
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(List<String> symbols) {
		return null;
	}

	@Override
	protected String getSubscribeBookTickerFrame(List<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(List<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(List<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(List<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}
}
