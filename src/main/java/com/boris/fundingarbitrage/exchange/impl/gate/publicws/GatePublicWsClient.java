package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;

import java.net.URI;

public class GatePublicWsClient extends PublicWsClient<GatePublicMessageHandler> {
	private static final URI endpoint = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt");
	private final ExchangeContext context;

	public GatePublicWsClient(ExchangeContext context, GatePublicMessageHandler messageHandler) {
		super(context, endpoint, messageHandler);
		this.context = context;
	}

	private void sendSubscribeFrame(String channel, String[] symbols) {
		this.prettyWsClient.sendObject(new WsRequest(channel, "subscribe", symbols));
	}

	private void sendUnsubscribeFrame(String channel, String[] symbols) {
		this.prettyWsClient.sendObject(new WsRequest(channel, "unsubscribe", symbols));
	}

	@Override
	protected void sendSubscribeFundingRateFrame(String[] symbols) {
		sendSubscribeFrame("futures.tickers", symbols);
		for (String symbol : symbols) {
			String coin = context.getSymbolInverse(symbol);
			this.messageHandler.updateFundingSettlementForCoin(coin);
		}
	}

	@Override
	protected void sendUnsubscribeFundingRateFrame(String[] symbols) {
		sendUnsubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected void sendSubscribeBookTickerFrame(String[] symbols) {
		sendSubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected void sendUnsubscribeBookTickerFrame(String[] symbols) {
		sendUnsubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected void sendSubscribeMarkPriceFrame(String[] symbols) {
		sendSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected void sendUnsubscribeMarkPriceFrame(String[] symbols) {
		sendUnsubscribeFrame("futures.tickers", symbols);
	}


}
