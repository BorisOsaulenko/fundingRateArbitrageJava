package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

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

	private String getMarkPriceStream(@NotNull String symbol) {
		return String.format("%s@markPrice@1s", symbol.toLowerCase());
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
		return this.getSubscribeFrame(symbols, this::getMarkPriceStream);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getMarkPriceStream);
	}

	@Override
	public void subscribeFundingRates(Set<String> coins, Consumer<FundingRatePatch> handler) {
		Set<String> coinsToSubscribe = new HashSet<>();
		for (String coin : coins) {
			fundingRateHandlers.computeIfAbsent(coin, v -> new HashSet<>()).add(handler);
			if (!fundingAndMarkPriceSubscribedCoins.contains(coin)) {
				coinsToSubscribe.add(coin);
				fundingAndMarkPriceSubscribedCoins.add(coin);
			}
		}

		if (!coinsToSubscribe.isEmpty()) sendMessage(getSubscribeFundingRateFrame(coinsToSubscribe));
	}

	@Override
	public void unsubscribeFundingRates(Set<String> coins) {
		Set<String> coinsToUnsubscribe = new HashSet<>();
		for (String coin : coins) {
			markPriceHandlers.remove(coin);
			if (fundingAndMarkPriceSubscribedCoins.contains(coin)) {
				coinsToUnsubscribe.add(coin);
				fundingAndMarkPriceSubscribedCoins.remove(coin);
			}
		}

		if (!coinsToUnsubscribe.isEmpty()) sendMessage(getUnsubscribeFundingRateFrame(coinsToUnsubscribe));
	}

	@Override
	public void removeFundingRatesHandler(Set<String> coins, Consumer<FundingRatePatch> handler) {
		for (String coin : coins) {
			var handlers = fundingRateHandlers.get(coin);
			if (handlers == null) continue;
			handlers.remove(handler);
			if (handlers.isEmpty()) {
				fundingRateHandlers.remove(coin, handlers);
			}
		}
	}
}
