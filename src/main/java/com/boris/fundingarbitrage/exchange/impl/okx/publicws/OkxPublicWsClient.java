package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class OkxPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://ws.okx.com:8443/ws/v5/public");

	public OkxPublicWsClient(ExchangeContext context, OkxPublicHttpClient publicHttp) {
		OkxPublicMessageHandler messageHandler = new OkxPublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp);
	}

	private String subscribe(String channel, List<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(channel, symbol));
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String unsubscribe(String channel, List<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(channel, symbol));
		}
		return new WsRequest("unsubscribe", args).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(List<String> symbols) {
		return subscribe("funding-rate", symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(List<String> symbols) {
		return unsubscribe("funding-rate", symbols);
	}

	@Override
	protected String getSubscribeBookTickerFrame(List<String> symbols) {
		return subscribe("tickers", symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(List<String> symbols) {
		return unsubscribe("tickers", symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(List<String> symbols) {
		return subscribe("mark-price", symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(List<String> symbols) {
		return unsubscribe("mark-price", symbols);
	}
}
