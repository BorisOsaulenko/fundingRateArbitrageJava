package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.util.wss.publicws.PublicWsFundingSettlementViaRest;

import java.net.URI;
import java.util.List;

public class GatePublicWsClient extends PublicWsFundingSettlementViaRest {
	private static final URI endpoint = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt");

	public GatePublicWsClient(
					ExchangeContext context,
					GatePublicMessageHandler messageHandler,
					GatePublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String getSubscribeFrame(String channel, List<String> symbols) {
		return new WsRequest(channel, "subscribe", symbols).toJson();
	}

	private String getUnsubscribeFrame(String channel, List<String> symbols) {
		return new WsRequest(channel, "unsubscribe", symbols).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(List<String> symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(List<String> symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getSubscribeBookTickerFrame(List<String> symbols) {
		return getSubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(List<String> symbols) {
		return getUnsubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(List<String> symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(List<String> symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}
}
