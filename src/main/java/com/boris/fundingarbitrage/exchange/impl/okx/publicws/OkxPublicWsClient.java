package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.okx.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;

import java.net.URI;

public class OkxPublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://ws.okx.com:8443/ws/v5/public");

	public OkxPublicWsClient(
					ExchangeContext context,
					OkxPublicMessageHandler messageHandler,
					OkxPublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String subscribe(String channel, String[] symbols) {
		WsRequest.Arg[] args = new WsRequest.Arg[symbols.length];
		for (int i = 0; i < symbols.length; i++) {
			args[i] = new WsRequest.Arg(channel, symbols[i]);
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String unsubscribe(String channel, String[] symbols) {
		WsRequest.Arg[] args = new WsRequest.Arg[symbols.length];
		for (int i = 0; i < symbols.length; i++) {
			args[i] = new WsRequest.Arg(channel, symbols[i]);
		}
		return new WsRequest("unsubscribe", args).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(String[] symbols) {
		return subscribe("funding-rate", symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(String[] symbols) {
		return unsubscribe("funding-rate", symbols);
	}

	@Override
	protected String getSubscribeBookTickerFrame(String[] symbols) {
		return subscribe("tickers", symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(String[] symbols) {
		return unsubscribe("tickers", symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(String[] symbols) {
		return subscribe("mark-price", symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(String[] symbols) {
		return unsubscribe("mark-price", symbols);
	}
}
