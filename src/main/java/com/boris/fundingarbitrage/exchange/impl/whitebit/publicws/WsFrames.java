package com.boris.fundingarbitrage.exchange.impl.whitebit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class WsFrames {
	private final ExchangeContext context;

	public WsFrames(ExchangeContext context) {
		this.context = context;
	}

	private String subscribe(String method, Set<String> coins, Function<String, String> toSymbol) {
		List<Object> params = coins.stream().map(toSymbol).collect(Collectors.toList());
		return new WsRequest(method, params).toJson();
	}

	public String getSubscribeFuturesBookTickerFrame(Set<String> coins) {
		return subscribe("bookTicker_subscribe", coins, context::getFuturesSymbol);
	}

	public String getSubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return subscribe("lastprice_subscribe", coins, context::getFuturesSymbol);
	}

	public String getSubscribeSpotBookTickerFrame(Set<String> coins) {
		return subscribe("bookTicker_subscribe", coins, context::getSpotSymbol);
	}

	public String getPingFrame() {
		WsRequest ping = new WsRequest("ping", new ArrayList<>());
		return ping.toJson();
	}
}
