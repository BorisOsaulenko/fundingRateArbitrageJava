package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;

import java.net.URI;

public class BitgetPublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://ws.bitget.com/v2/ws/public");
	private static final String instType = "USDT-FUTURES";
	private static final String tickerChannel = "ticker";

	public BitgetPublicWsClient(
					ExchangeContext context,
					BitgetPublicMessageHandler messageHandler,
					BitgetPublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String getSubscribeFrame(String[] symbols) {
		WsRequest.Arg[] args = new WsRequest.Arg[symbols.length];
		for (int i = 0; i < symbols.length; i++) {
			args[i] = new WsRequest.Arg(instType, BitgetPublicWsClient.tickerChannel, symbols[i]);
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String getUnsubscribeFrame(String[] symbols) {
		WsRequest.Arg[] args = new WsRequest.Arg[symbols.length];
		for (int i = 0; i < symbols.length; i++) {
			args[i] = new WsRequest.Arg(instType, BitgetPublicWsClient.tickerChannel, symbols[i]);
		}
		return new WsRequest("unsubscribe", args).toJson();
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
