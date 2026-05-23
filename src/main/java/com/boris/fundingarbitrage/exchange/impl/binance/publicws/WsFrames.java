package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class WsFrames {
	private final ExchangeContext context;

	public WsFrames(ExchangeContext context) {
		this.context = context;
	}

	private String getSubscribeFrame(
					Set<String> coins,
					Function<String, String> toSymbol,
					Function<String, String> toStreamMapper
	) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
		String[] streams = symbols.stream().map(toStreamMapper).toArray(String[]::new);
		WsRequest sub = new WsRequest("SUBSCRIBE", streams);
		return sub.toJson();
	}

	private String getUnsubscribeFrame(
					Set<String> coins,
					Function<String, String> toSymbol,
					Function<String, String> toStreamMapper
	) {
		Set<String> symbols = coins.stream().map(toSymbol).collect(Collectors.toSet());
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

	public String getSubscribeFuturesFundingRateFrame(Set<String> coins) {
		return this.getSubscribeFrame(coins, context::getFuturesSymbol, this::getFundingRateStream);
	}

	public String getUnsubscribeFuturesFundingRateFrame(Set<String> coins) {
		return this.getUnsubscribeFrame(coins, context::getFuturesSymbol, this::getFundingRateStream);
	}

	public String getSubscribeFuturesBookTickerFrame(Set<String> coins) {
		return this.getSubscribeFrame(coins, context::getFuturesSymbol, this::getBookTickerStream);
	}

	public String getUnsubscribeFuturesBookTickerFrame(Set<String> coins) {
		return this.getUnsubscribeFrame(coins, context::getFuturesSymbol, this::getBookTickerStream);
	}

	public String getSubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return this.getSubscribeFrame(coins, context::getFuturesSymbol, this::getFundingRateStream);
	}

	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> coins) {
		return this.getUnsubscribeFrame(coins, context::getFuturesSymbol, this::getFundingRateStream);
	}

	public String getSubscribeSpotBookTickerFrame(Set<String> coins) {
		return this.getSubscribeFrame(coins, context::getSpotSymbol, this::getBookTickerStream);
	}

	public String getUnsubscribeSpotBookTickerFrame(Set<String> coins) {
		return this.getUnsubscribeFrame(coins, context::getSpotSymbol, this::getBookTickerStream);
	}
}
