package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class WsFrames {
	private final ExchangeContext context;

	public WsFrames(ExchangeContext context) {
		this.context = context;
	}

	private String getSubscribeFrame(String channel, Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		return new WsRequest(channel, "subscribe", symbols).toJson();
	}

	private String getUnsubscribeFrame(String channel, Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		return new WsRequest(channel, "unsubscribe", symbols).toJson();
	}

	public String getSubscribeFuturesFundingRateFrame(Set<String> coins) {
		return getSubscribeFrame("futures.tickers", coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesFundingRateFrame(Set<String> coins) {
		return getUnsubscribeFrame("futures.tickers", coins, context::getFuturesSymbol);
	}

	public String getSubscribeFuturesBookTickerFrame(Set<String> coins) {
		return getSubscribeFrame("futures.book_ticker", coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesBookTickerFrame(Set<String> coins) {
		return getUnsubscribeFrame("futures.book_ticker", coins, context::getFuturesSymbol);
	}

	public String getSubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return getSubscribeFrame("futures.tickers", coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return getUnsubscribeFrame("futures.tickers", coins, context::getFuturesSymbol);
	}

	public String getSubscribeSpotBookTickerFrame(Set<String> coins) {
		return getSubscribeFrame("spot.book_ticker", coins, context::getSpotSymbol);
	}

	public String getUnsubscribeSpotBookTickerFrame(Set<String> coins) {
		return getUnsubscribeFrame("spot.book_ticker", coins, context::getSpotSymbol);
	}

	public String getSpotPingFrame() {
		return "spot.ping";
	}

	public String getFuturesPingFrame() {
		return "futures.ping";
	}
}