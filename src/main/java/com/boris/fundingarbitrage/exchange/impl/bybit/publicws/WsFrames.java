package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import java.util.List;
import java.util.Set;

class WsFrames {
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

	public String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	public String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	public String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getSubscribeBookTickerFrame(symbols);
	}

	public String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeBookTickerFrame(symbols);
	}

	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeMarkPriceFrame(symbols);
	}

	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeMarkPriceFrame(symbols);
	}

	public String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getSubscribeBookTickerFrame(symbols);
	}

	public String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeBookTickerFrame(symbols);
	}

	public String getPingFrame() {
		return null;
	}
}
