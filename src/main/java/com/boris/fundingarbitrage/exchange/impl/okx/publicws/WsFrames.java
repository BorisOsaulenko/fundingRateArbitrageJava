package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;

import java.util.ArrayList;
import java.util.Set;

class WsFrames implements IPublicWsFrames {
	private String subscribe(String channel, Set<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(channel, symbol));
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String unsubscribe(String channel, Set<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(channel, symbol));
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
		return subscribe("tickers", symbols);
	}

	@Override
	public String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return unsubscribe("tickers", symbols);
	}

	@Override
	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return subscribe("mark-price", symbols);
	}

	@Override
	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return unsubscribe("mark-price", symbols);
	}

	@Override
	public String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return subscribe("tickers", symbols);
	}

	@Override
	public String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return unsubscribe("tickers", symbols);
	}

	@Override
	public String getPingFrame() {
		return "ping";
	}
}
