package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import java.util.ArrayList;
import java.util.Set;

class WsFrames implements IPublicWsFrames {
	private static final String instType = "USDT-FUTURES";
	private static final String tickerChannel = "ticker";

	private String getSubscribeFrame(Set<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(instType, tickerChannel, symbol));
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String getUnsubscribeFrame(Set<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(instType, tickerChannel, symbol));
		}
		return new WsRequest("unsubscribe", args).toJson();
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
		return getSubscribeFrame(symbols);
	}

	@Override
	public String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	public String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	public String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	public String getPingFrame() {
		return "ping";
	}
}
