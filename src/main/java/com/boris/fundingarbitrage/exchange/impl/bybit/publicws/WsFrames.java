package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class WsFrames {
	private final ExchangeContext context;

	public WsFrames(ExchangeContext context) {
		this.context = context;
	}

	private String getSubscribeBookTickerFrame(Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		List<String> topics = symbols.stream().map(symbol -> "orderbook.1." + symbol).toList();
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeBookTickerFrame(Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		List<String> topics = symbols.stream().map(symbol -> "orderbook.1." + symbol).toList();
		return new WsRequest("unsubscribe", topics).toJson();
	}

	private String getSubscribeMarkPriceFrame(Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeMarkPriceFrame(Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("unsubscribe", topics).toJson();
	}

	public String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	public String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	public String getSubscribeFuturesBookTickerFrame(Set<String> coins) {
		return getSubscribeBookTickerFrame(coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesBookTickerFrame(Set<String> coins) {
		return getUnsubscribeBookTickerFrame(coins, context::getFuturesSymbol);
	}

	public String getSubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return getSubscribeMarkPriceFrame(coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return getUnsubscribeMarkPriceFrame(coins, context::getFuturesSymbol);
	}

	public String getSubscribeSpotBookTickerFrame(Set<String> coins) {
		return getSubscribeBookTickerFrame(coins, context::getSpotSymbol);
	}

	public String getUnsubscribeSpotBookTickerFrame(Set<String> coins) {
		return getUnsubscribeBookTickerFrame(coins, context::getSpotSymbol);
	}

	public String getPingFrame() {
		return new PingFrame().toJson();
	}
}
