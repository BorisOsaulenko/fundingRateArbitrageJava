package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class WsFrames {
	private static final String instType = "USDT-FUTURES";
	private static final String tickerChannel = "ticker";
	private final ExchangeContext context;

	public WsFrames(ExchangeContext context) {
		this.context = context;
	}

	private String getSubscribeFrame(Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(instType, tickerChannel, symbol));
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String getUnsubscribeFrame(Set<String> coins, Function<String, String> toSymbol) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(instType, tickerChannel, symbol));
		}
		return new WsRequest("unsubscribe", args).toJson();
	}

	public String getSubscribeFuturesBookTickerFrame(Set<String> coins) {
		return getSubscribeFrame(coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesBookTickerFrame(Set<String> coins) {
		return getUnsubscribeFrame(coins, context::getFuturesSymbol);
	}

	public String getSubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return getSubscribeFrame(coins, context::getFuturesSymbol);
	}

	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return getUnsubscribeFrame(coins, context::getFuturesSymbol);
	}

	public String getSubscribeSpotBookTickerFrame(Set<String> coins) {
		return getSubscribeFrame(coins, context::getSpotSymbol);
	}

	public String getUnsubscribeSpotBookTickerFrame(Set<String> coins) {
		return getUnsubscribeFrame(coins, context::getSpotSymbol);
	}

	public String getPingFrame() {
		return "ping";
	}
}
