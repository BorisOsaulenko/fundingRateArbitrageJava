package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Function;

class WsFrames implements IPublicWsFrames {
	private String getSubscribeFrame(Set<String> symbols, Function<String, String> toStreamMapper) {
		String[] streams = symbols.stream().map(toStreamMapper).toArray(String[]::new);
		WsRequest sub = new WsRequest("SUBSCRIBE", streams);
		return sub.toJson();
	}

	private String getUnsubscribeFrame(Set<String> symbols, Function<String, String> toStreamMapper) {
		String[] streams = symbols.stream().map(toStreamMapper).toArray(String[]::new);
		WsRequest unsub = new WsRequest("UNSUBSCRIBE", streams);
		return unsub.toJson();
	}

	private String getFundingRateStream(@NotNull String symbol) {
		return String.format("%s@markPrice@1s", symbol.toLowerCase());
	}

	private String getBookTickerStream(@NotNull String symbol) {
		return String.format("%s@bookTicker", symbol.toLowerCase());
	}

	@Override
	public String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return this.getSubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	public String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	public String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return this.getSubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	public String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return null;
	}

	@Override
	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return null;
	}

	@Override
	public String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return this.getSubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	public String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	public String getPingFrame() {
		return null;
	}
}
