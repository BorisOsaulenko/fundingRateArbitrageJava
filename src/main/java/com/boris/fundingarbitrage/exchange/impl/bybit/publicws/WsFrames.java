package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;

import java.util.List;
import java.util.Set;

class WsFrames implements IPublicWsFrames {
	private String getSubscribeBookTickerFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "orderbook.1." + symbol).toList();
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeBookTickerFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "orderbook.1." + symbol).toList();
		return new WsRequest("unsubscribe", topics).toJson();
	}

	private String getSubscribeMarkPriceFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeMarkPriceFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("unsubscribe", topics).toJson();
	}

	@Override
	public String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	public String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	public String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getSubscribeBookTickerFrame(symbols);
	}

	@Override
	public String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeBookTickerFrame(symbols);
	}

	@Override
	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeMarkPriceFrame(symbols);
	}

	@Override
	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeMarkPriceFrame(symbols);
	}

	@Override
	public String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getSubscribeBookTickerFrame(symbols);
	}

	@Override
	public String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeBookTickerFrame(symbols);
	}

	@Override
	public String getPingFrame() {
		return null;
	}
}
