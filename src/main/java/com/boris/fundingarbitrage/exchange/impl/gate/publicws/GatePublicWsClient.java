package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.util.wss.publicws.PublicWsFundingSettlementViaRest;

import java.net.URI;
import java.util.Set;

public class GatePublicWsClient extends PublicWsFundingSettlementViaRest {
	private static final URI endpoint = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt");

	public GatePublicWsClient(ExchangeContext context, GatePublicHttpClient publicHttp) {
		GatePublicMessageHandler messageHandler = new GatePublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String getSubscribeFrame(String channel, Set<String> symbols) {
		return new WsRequest(channel, "subscribe", symbols).toJson();
	}

	private String getUnsubscribeFrame(String channel, Set<String> symbols) {
		return new WsRequest(channel, "unsubscribe", symbols).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getSubscribeBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}
}
