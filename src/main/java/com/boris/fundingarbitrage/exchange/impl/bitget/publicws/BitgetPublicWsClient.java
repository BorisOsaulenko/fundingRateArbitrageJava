package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;

import java.net.URI;

public class BitgetPublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://ws.bitget.com/v2/ws/public");
	private static final String instType = "USDT-FUTURES";
	private static final String tickerChannel = "ticker";

	public BitgetPublicWsClient(ExchangeContext context, PublicMessageHandler messageHandler) {
		super(context, endpoint, messageHandler);
	}

	private void sendSubscribeFrame(String[] symbols) {
		WsRequest.Arg[] args = new WsRequest.Arg[symbols.length];
		for (int i = 0; i < symbols.length; i++) {
			args[i] = new WsRequest.Arg(instType, BitgetPublicWsClient.tickerChannel, symbols[i]);
		}
		this.prettyWsClient.sendObject(new WsRequest("subscribe", args));
	}

	private void sendUnsubscribeFrame(String[] symbols) {
		WsRequest.Arg[] args = new WsRequest.Arg[symbols.length];
		for (int i = 0; i < symbols.length; i++) {
			args[i] = new WsRequest.Arg(instType, BitgetPublicWsClient.tickerChannel, symbols[i]);
		}
		this.prettyWsClient.sendObject(new WsRequest("unsubscribe", args));
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
	protected void sendUnsubscribeMarkPriceFrame(String[] symbols) {
		sendUnsubscribeFrame(symbols);
	}
}
