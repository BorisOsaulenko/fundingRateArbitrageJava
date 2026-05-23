package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

class WsFrames {
	private final ExchangeContext context;
	private final String tickerTopic = "/contractMarket/tickerV2:";
	private final String spotBookTickerTopic = "/spotMarket/level1:";
	private final String markPriceTopic = "/contract/instrument:";

	public WsFrames(ExchangeContext context) {
		this.context = context;
	}

	private String getSubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "subscribe", topic, false, true);
		return request.toJson();
	}

	private String getUnsubscribeFrame(String topic) {
		WsRequest request = new WsRequest(UUID.randomUUID().toString(), "unsubscribe", topic, false, true);
		return request.toJson();
	}

	private String joinSymbols(Set<String> coins, Function<String, String> toSymbol) {
		return coins.stream().map(toSymbol).collect(Collectors.joining(","));
	}

	public String getSubscribeFuturesBookTickerFrame(Set<String> coins) {
		return getSubscribeFrame(tickerTopic + joinSymbols(coins, context::getFuturesSymbol));
	}

	public String getUnsubscribeFuturesBookTickerFrame(Set<String> coins) {
		return getUnsubscribeFrame(tickerTopic + joinSymbols(coins, context::getFuturesSymbol));
	}

	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame(markPriceTopic + joinSymbols(symbols, context::getFuturesSymbol));
	}

	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame(markPriceTopic + joinSymbols(symbols, context::getFuturesSymbol));
	}

	public String getSubscribeSpotBookTickerFrame(Set<String> coins) {
		return getSubscribeFrame(spotBookTickerTopic + joinSymbols(coins, context::getSpotSymbol));
	}

	public String getUnsubscribeSpotBookTickerFrame(Set<String> coins) {
		return getUnsubscribeFrame(spotBookTickerTopic + joinSymbols(coins, context::getSpotSymbol));
	}

	public String getPingFrame() {
		return new PingFrame().toJson();
	}
}
