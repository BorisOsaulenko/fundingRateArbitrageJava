package com.boris.fundingarbitrage.exchange.impl.whitebit.publicws;

import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class WsFrames implements IPublicWsFrames {
	private String subscribe(String method, Set<String> symbols) {
		List<Object> params = new ArrayList<>(symbols);
		return new WsRequest(System.currentTimeMillis(), method, params).toJson();
	}

	private String unsubscribe(String method, Set<String> symbols) {
		List<Object> params = new ArrayList<>();
		return new WsRequest(System.currentTimeMillis(), method, params).toJson();
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
		return subscribe("bookTicker_subscribe", symbols);
	}

	@Override
	public String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return unsubscribe("bookTicker_unsubscribe", symbols);
	}

	@Override
	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return subscribe("lastprice_subscribe", symbols);
	}

	@Override
	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return unsubscribe("lastprice_unsubscribe", symbols);
	}

	@Override
	public String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return subscribe("bookTicker_subscribe", symbols);
	}

	@Override
	public String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return unsubscribe("bookTicker_unsubscribe", symbols);
	}

	@Override
	public String getPingFrame() {
		WsRequest ping = new WsRequest(System.currentTimeMillis(), "ping", new ArrayList<>());
		return ping.toJson();
	}
}
