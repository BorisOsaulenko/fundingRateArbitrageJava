package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos.SubscribePOJO;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos.UnsubscribePOJO;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class BinancePublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://fstream.binance.com/ws");
	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

	public BinancePublicWsClient(ExchangeContext context, BinancePublicMessageHandler messageHandler) {
		super(context, endpoint, messageHandler);
	}

	public static int getNextId() {
		return NEXT_ID.getAndIncrement();
	}

	private void sendSubscribeFrame(String[] symbols, Function<String, String> toStreamMapper) {
		String[] streams = Arrays.stream(symbols).map(toStreamMapper).toArray(String[]::new);
		SubscribePOJO sub = new SubscribePOJO(streams);
		this.prettyWsClient.sendObject(sub);
	}

	private void sendUnsubscribeFrame(String[] symbols, Function<String, String> toStreamMapper) {
		String[] streams = Arrays.stream(symbols).map(toStreamMapper).toArray(String[]::new);
		UnsubscribePOJO unsub = new UnsubscribePOJO(streams);
		this.prettyWsClient.sendObject(unsub);
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
	protected void sendSubscribeFundingRateFrame(String[] symbols) {
		this.sendSubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	protected void sendUnsubscribeFundingRateFrame(String[] symbols) {
		this.sendUnsubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	protected void sendSubscribeBookTickerFrame(String[] symbols) {
		this.sendSubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected void sendUnsubscribeBookTickerFrame(String[] symbols) {
		this.sendUnsubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected void sendSubscribeMarkPriceFrame(String[] symbols) {
		this.sendSubscribeFrame(symbols, this::getMarkPriceStream);
	}

	@Override
	protected void sendUnsubscribeMarkPriceFrame(String[] symbols) {
		this.sendUnsubscribeFrame(symbols, this::getMarkPriceStream);
	}
}
