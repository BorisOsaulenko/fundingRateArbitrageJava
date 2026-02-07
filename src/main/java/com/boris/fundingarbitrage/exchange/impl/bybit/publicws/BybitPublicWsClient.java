package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;

import java.net.URI;
import java.util.Arrays;

public class BybitPublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://stream.bybit.com/v5/public/linear");

	public BybitPublicWsClient(ExchangeContext context, BybitPublicMessageHandler messageHandler) {
		super(context, endpoint, messageHandler);
	}

	private void sendSubscribeFrame(String[] symbols) {
		String[] topics = Arrays.stream(symbols).map(symbol -> "tickers." + symbol).toArray(String[]::new);
		this.prettyWsClient.sendObject(new WsRequest("subscribe", topics));
	}

	private void sendUnsubscribeFrame(String[] symbols) {
		String[] topics = Arrays.stream(symbols).map(symbol -> "tickers." + symbol).toArray(String[]::new);
		this.prettyWsClient.sendObject(new WsRequest("unsubscribe", topics));
	}

	@Override
	protected void sendSubscribeFundingRateFrame(String[] symbols) {
		sendSubscribeFrame(symbols);
	}

	@Override
	protected void sendUnsubscribeFundingRateFrame(String[] symbols) {
		sendUnsubscribeFrame(symbols);
	}

	@Override
	protected void sendSubscribeBookTickerFrame(String[] symbols) {
		sendSubscribeFrame(symbols);
	}

	@Override
	protected void sendUnsubscribeBookTickerFrame(String[] symbols) {
		sendUnsubscribeFrame(symbols);
	}

	@Override
	protected void sendSubscribeMarkPriceFrame(String[] symbols) {
		sendSubscribeFrame(symbols);
	}

	@Override
	protected void sendUnsubscribeMarkPriceFrame(String[] symbols) {sendUnsubscribeFrame(symbols);}
}
