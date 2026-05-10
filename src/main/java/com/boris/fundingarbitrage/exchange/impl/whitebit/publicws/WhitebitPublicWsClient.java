package com.boris.fundingarbitrage.exchange.impl.whitebit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;
import lombok.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class WhitebitPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://api.whitebit.com/ws");
	private static final long PING_INTERVAL_SECONDS = 50;

	public WhitebitPublicWsClient(ExchangeContext context, WhitebitPublicHttpClient publicHttp) {
		WhitebitPublicMessageHandler messageHandler = new WhitebitPublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
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
	protected String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return subscribe("bookTicker_subscribe", symbols);
	}

	@Override
	protected String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return unsubscribe("bookTicker_unsubscribe", symbols);
	}

	@Override
	protected String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return subscribe("lastprice_subscribe", symbols);
	}

	@Override
	protected String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return unsubscribe("lastprice_unsubscribe", symbols);
	}

	@Override
	protected String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return subscribe("bookTicker_subscribe", symbols);
	}

	@Override
	protected String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return unsubscribe("bookTicker_unsubscribe", symbols);
	}

	@Override
	public void subscribeFuturesBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		for (String coin : coins) {
			String symbol = context.getFuturesSymbol(coin);
			sendMessage(getSubscribeFuturesBookTickerFrame(Set.of(symbol)));
			Set<Consumer<@NonNull BookTickerPatch>> handlers = futuresBookTickerHandlers.get(coin);
			if (handlers == null) futuresBookTickerHandlers.put(coin, handlers = new HashSet<>());
			handlers.add(handler);
		}
	} // Whitebit does not allow subscribing to multiple book tickers at once, so we subscribe to each one separately.

	@Override
	public void subscribeSpotBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		for (String coin : coins) {
			String symbol = context.getFuturesSymbol(coin);
			sendMessage(getSubscribeSpotBookTickerFrame(Set.of(symbol)));
			Set<Consumer<@NonNull BookTickerPatch>> handlers = spotBookTickerHandlers.get(coin);
			if (handlers == null) spotBookTickerHandlers.put(coin, handlers = new HashSet<>());
			handlers.add(handler);
		}
	}

	@Override
	protected String getPingFrame() {
		WsRequest ping = new WsRequest(System.currentTimeMillis(), "ping", new ArrayList<>());
		return ping.toJson();
	}
}
