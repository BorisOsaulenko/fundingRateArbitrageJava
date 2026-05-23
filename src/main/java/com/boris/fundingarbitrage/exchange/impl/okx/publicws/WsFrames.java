package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class WsFrames {
	private final ExchangeContext context;

	public WsFrames(ExchangeContext context) {
		this.context = context;
	}

	private String subscribe(String channel, Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(channel, symbol));
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String unsubscribe(String channel, Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(channel, symbol));
		}
		return new WsRequest("unsubscribe", args).toJson();
	}

	public String getSubscribeFuturesFundingRateFrame(Set<String> coins) {
		return null;
	}

	public String getUnsubscribeFuturesFundingRateFrame(Set<String> coins) {
		return null;
	}
	
	public String getSubscribeFuturesBookTickerFrame(Set<String> coins) {
		return subscribe("tickers", coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesBookTickerFrame(Set<String> coins) {
		return unsubscribe("tickers", coins, context::getFuturesSymbol);
	}

	public String getSubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return subscribe("mark-price", coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return unsubscribe("mark-price", coins, context::getFuturesSymbol);
	}

	public String getSubscribeSpotBookTickerFrame(Set<String> coins) {
		return subscribe("tickers", coins, context::getSpotSymbol);
	}

	public String getUnsubscribeSpotBookTickerFrame(Set<String> coins) {
		return unsubscribe("tickers", coins, context::getSpotSymbol);
	}

	public String getPingFrame() {
		return "ping";
	}
}
