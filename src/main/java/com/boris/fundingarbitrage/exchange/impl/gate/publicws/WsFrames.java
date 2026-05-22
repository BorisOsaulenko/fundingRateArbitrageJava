package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;

import java.util.Set;

class WsFrames implements IPublicWsFrames {
	private String getSubscribeFrame(String channel, Set<String> symbols) {
		return new WsRequest(channel, "subscribe", symbols).toJson();
	}

	private String getUnsubscribeFrame(String channel, Set<String> symbols) {
		return new WsRequest(channel, "unsubscribe", symbols).toJson();
	}

	@Override
	public String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	public String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}

	@Override
	public String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	public String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.tickers", symbols);
	}

	@Override
	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.tickers", symbols);
	}

	@Override
	public String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	public String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame("futures.book_ticker", symbols);
	}

	@Override
	public String getPingFrame() {
		return null;
	}
}
