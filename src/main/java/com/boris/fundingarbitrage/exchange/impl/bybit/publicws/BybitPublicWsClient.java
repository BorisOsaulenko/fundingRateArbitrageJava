package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.List;
import java.util.Set;

public class BybitPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://stream.bybit.com/v5/public/linear");

	public BybitPublicWsClient(ExchangeContext context, BybitPublicHttpClient publicHttp) {
		BybitPublicMessageHandler messageHandler = new BybitPublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String getSubscribeFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("unsubscribe", topics).toJson();
	}

	@Override
	protected String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	protected String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}
}
