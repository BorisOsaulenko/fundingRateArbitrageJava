package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos.SubscribePOJO;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos.UnsubscribePOJO;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class BinancePublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://fstream.binance.com/ws");
	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

	public BinancePublicWsClient(
					ExchangeContext context,
					BinancePublicMessageHandler messageHandler,
					PublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
	}

	public static int getNextId() {
		return NEXT_ID.getAndIncrement();
	}

	private String getSubscribeFrame(String[] symbols, Function<String, String> toStreamMapper) {
		String[] streams = Arrays.stream(symbols).map(toStreamMapper).toArray(String[]::new);
		SubscribePOJO sub = new SubscribePOJO(streams);
		return sub.toJson();
	}

	private String getUnsubscribeFrame(String[] symbols, Function<String, String> toStreamMapper) {
		String[] streams = Arrays.stream(symbols).map(toStreamMapper).toArray(String[]::new);
		UnsubscribePOJO unsub = new UnsubscribePOJO(streams);
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
	protected String getSubscribeFundingRateFrame(String[] symbols) {
		return this.getSubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(String[] symbols) {
		return this.getUnsubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	protected String getSubscribeBookTickerFrame(String[] symbols) {
		return this.getSubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(String[] symbols) {
		return this.getUnsubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(String[] symbols) {
		return this.getSubscribeFrame(symbols, this::getMarkPriceStream);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(String[] symbols) {
		return this.getUnsubscribeFrame(symbols, this::getMarkPriceStream);
	}
}
