package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.util.wss.publicws.FundingSettlementViaRest;

import java.net.URI;

public class GatePublicWsClient extends FundingSettlementViaRest {
	private static final URI endpoint = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt");

	public GatePublicWsClient(
					ExchangeContext context,
					GatePublicMessageHandler messageHandler,
					GatePublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String getSubscribeFrame(String channel, String[] symbols) {
		return new WsRequest(channel, "subscribe", symbols).toJson();
	}

	private String getUnsubscribeFrame(String channel, String[] symbols) {
		return new WsRequest(channel, "unsubscribe", symbols).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(String[] symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(String[] symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getSubscribeBookTickerFrame(String[] symbols) {
		return getSubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(String[] symbols) {
		return getUnsubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(String[] symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(String[] symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}
}
