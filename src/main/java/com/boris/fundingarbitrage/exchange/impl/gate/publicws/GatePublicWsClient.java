package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FundingSettlementViaRest;

import java.net.URI;
import java.util.Set;

public class GatePublicWsClient extends FundingSettlementViaRest {
	private static final URI endpoint = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt");

	public GatePublicWsClient(ExchangeContext context, GatePublicHttpClient publicHttp) {
		GatePublicMessageHandler messageHandler = new GatePublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
	}

	private String getSubscribeFrame(String channel, Set<String> symbols) {
		return new WsRequest(channel, "subscribe", symbols).toJson();
	}

	private String getUnsubscribeFrame(String channel, Set<String> symbols) {
		return new WsRequest(channel, "unsubscribe", symbols).toJson();
	}

	@Override
	protected String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}

	@Override
	protected String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	protected String getSpotPingFrame() {
		return null;
	}
}
