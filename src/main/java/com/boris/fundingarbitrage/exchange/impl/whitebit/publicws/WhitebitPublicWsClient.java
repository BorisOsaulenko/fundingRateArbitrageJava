package com.boris.fundingarbitrage.exchange.impl.whitebit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.ws.WsRequest;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WhitebitPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://api.whitebit.com/ws");
	private static final long PING_INTERVAL_SECONDS = 50;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	public WhitebitPublicWsClient(
					ExchangeContext context,
					WhitebitPublicMessageHandler messageHandler,
					WhitebitPublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
		startPingLoop();
	}

	private void startPingLoop() {
		scheduler.scheduleAtFixedRate(
						() -> {
							WsRequest ping = new WsRequest(System.currentTimeMillis(), "ping", new Object[]{});
							sendObject(ping);
						}, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS
		);
	}

	private String subscribe(String method, String[] symbols) {
		Object[] params = symbols;
		return new WsRequest(System.currentTimeMillis(), method, params).toJson();
	}

	private String unsubscribe(String method, String[] symbols) {
		Object[] params = new Object[]{};
		return new WsRequest(System.currentTimeMillis(), method, params).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(String[] symbols) {
		return subscribe("market_subscribe", symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(String[] symbols) {
		return unsubscribe("market_unsubscribe", symbols);
	}

	@Override
	protected String getSubscribeBookTickerFrame(String[] symbols) {
		return subscribe("bookTicker_subscribe", symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(String[] symbols) {
		return unsubscribe("bookTicker_unsubscribe", symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(String[] symbols) {
		return subscribe("lastprice_subscribe", symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(String[] symbols) {
		return unsubscribe("lastprice_unsubscribe", symbols);
	}

	@Override
	public void close() {
		super.close();
		scheduler.shutdownNow();
	}
}
