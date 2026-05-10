package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Set;

public class OkxPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://ws.okx.com:8443/ws/v5/public");

	public OkxPublicWsClient(ExchangeContext context, OkxPublicHttpClient publicHttp) {
		OkxPublicMessageHandler messageHandler = new OkxPublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
	}

	private String subscribe(String channel, Set<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(channel, symbol));
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String unsubscribe(String channel, Set<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(channel, symbol));
		}
		return new WsRequest("unsubscribe", args).toJson();
	}

	@Override
	protected String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return subscribe("funding-rate", symbols);
	}

	@Override
	protected String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return unsubscribe("funding-rate", symbols);
	}

	@Override
	protected String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return subscribe("tickers", symbols);
	}

	@Override
	protected String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return unsubscribe("tickers", symbols);
	}

	@Override
	protected String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return subscribe("mark-price", symbols);
	}

	@Override
	protected String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return unsubscribe("mark-price", symbols);
	}

	@Override
	protected String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return subscribe("tickers", symbols);
	}

	@Override
	protected String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return unsubscribe("tickers", symbols);
	}

	@Override
	protected String getPingFrame() {
		return null;
	}
}
