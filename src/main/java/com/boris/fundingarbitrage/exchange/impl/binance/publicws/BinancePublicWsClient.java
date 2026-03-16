package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BinancePublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://fstream.binance.com/ws");
	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
	private final Set<String> fundingAndMarkPriceSubscribedCoins = new HashSet<>();

	public BinancePublicWsClient(ExchangeContext context, PublicHttpClient publicHttp) {
		BinancePublicMessageHandler messageHandler = new BinancePublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp);
	}

	public static int getNextId() {
		return NEXT_ID.getAndIncrement();
	}

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
	protected String getSubscribeFundingRateFrame(Set<String> symbols) {
		return this.getSubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	protected String getSubscribeBookTickerFrame(Set<String> symbols) {
		return this.getSubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(Set<String> symbols) {
		return null;
	}

	private <T> void subscribeFundingAndMark(
					Set<String> coins,
					Consumer<T> handler,
					CoinVector<Set<Consumer<T>>> handlersMap
	) {
		Set<String> coinsToSubscribe = new HashSet<>();
		for (String coin : coins) {
			handlersMap.computeIfAbsent(coin, v -> new HashSet<>()).add(handler);
			if (!fundingAndMarkPriceSubscribedCoins.contains(coin)) {
				coinsToSubscribe.add(coin);
				fundingAndMarkPriceSubscribedCoins.add(coin);
			}
		}

		Set<String> symbolsToSubscribe = coinsToSubscribe.stream().map(context::getSymbol).collect(Collectors.toSet());
		if (!coinsToSubscribe.isEmpty()) sendMessage(getSubscribeFundingRateFrame(symbolsToSubscribe));
	}

	private <T> void unsubscribeFundingAndMark(
					Set<String> coins,
					CoinVector<Set<Consumer<T>>> handlersMap
	) {
		Set<String> coinsToUnsubscribe = new HashSet<>();
		for (String coin : coins) {
			handlersMap.remove(coin);
			if (fundingAndMarkPriceSubscribedCoins.contains(coin)) {
				coinsToUnsubscribe.add(coin);
				fundingAndMarkPriceSubscribedCoins.remove(coin);
			}
		}

		Set<String> symbolsToUnsubscribe = coinsToUnsubscribe.stream().map(context::getSymbol).collect(Collectors.toSet());
		if (!coinsToUnsubscribe.isEmpty()) sendMessage(getUnsubscribeFundingRateFrame(symbolsToUnsubscribe));
	}

	@Override
	public void subscribeFundingRates(Set<String> coins, Consumer<FundingRatePatch> handler) {
		subscribeFundingAndMark(coins, handler, fundingRateHandlers);
	}

	@Override
	public void unsubscribeFundingRates(Set<String> coins) {
		unsubscribeFundingAndMark(coins, fundingRateHandlers);
	}

	@Override
	public void subscribeMarkPrice(Set<String> coins, Consumer<MarkPricePatch> handler) {
		subscribeFundingAndMark(coins, handler, markPriceHandlers);
	}

	@Override
	public void unsubscribeMarkPrice(Set<String> coins) {
		unsubscribeFundingAndMark(coins, markPriceHandlers);
	}
}
