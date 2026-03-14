package com.boris.fundingarbitrage.exchange.impl.whitebit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;
import lombok.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WhitebitPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://api.whitebit.com/ws");
	private static final long PING_INTERVAL_SECONDS = 50;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	public WhitebitPublicWsClient(ExchangeContext context, WhitebitPublicHttpClient publicHttp) {
		WhitebitPublicMessageHandler messageHandler = new WhitebitPublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp);
		startPingLoop();
	}

	private void startPingLoop() {
		scheduler.scheduleAtFixedRate(
						() -> {
							WsRequest ping = new WsRequest(System.currentTimeMillis(), "ping", new ArrayList<>());
							sendObject(ping);
						}, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS
		);
	}

	private String subscribe(String method, Set<String> symbols) {
		List<Object> params = new ArrayList<>(symbols);
		return new WsRequest(System.currentTimeMillis(), method, params).toJson();
	}

	private String unsubscribe(String method, Set<String> symbols) {
		List<Object> params = new ArrayList<>();
		return new WsRequest(System.currentTimeMillis(), method, params).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getSubscribeBookTickerFrame(Set<String> symbols) {
		return subscribe("bookTicker_subscribe", symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(Set<String> symbols) {
		return unsubscribe("bookTicker_unsubscribe", symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(Set<String> symbols) {
		return subscribe("lastprice_subscribe", symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(Set<String> symbols) {
		return unsubscribe("lastprice_unsubscribe", symbols);
	}

	@Override
	public void subscribeBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		for (String coin : coins) {
			String symbol = context.getSymbol(coin);
			sendMessage(getSubscribeBookTickerFrame(Set.of(symbol)));
			Set<Consumer<@NonNull BookTickerPatch>> handlers = bookTickerHandlers.get(coin);
			if (handlers == null) bookTickerHandlers.put(coin, handlers = new HashSet<>());
			handlers.add(handler);
		}
	} // Whitebit does not allow subscribing to multiple book tickers at once, so we subscribe to each one separately.

	@Override
	public void close() {
		super.close();
		scheduler.shutdownNow();
	}
}
