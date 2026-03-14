package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BitgetPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://ws.bitget.com/v2/ws/public");
	private static final String instType = "USDT-FUTURES";
	private static final String tickerChannel = "ticker";
	private final ScheduledExecutorService pingExecutor = new ScheduledThreadPoolExecutor(1);

	public BitgetPublicWsClient(BitgetContext context, BitgetPublicHttpClient publicHttp) {
		BitgetPublicMessageHandler messageHandler = new BitgetPublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp);
		pingExecutor.scheduleAtFixedRate(this::sendPing, 5, 30, TimeUnit.SECONDS);
	}

	private String getSubscribeFrame(Set<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(instType, BitgetPublicWsClient.tickerChannel, symbol));
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String getUnsubscribeFrame(Set<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(instType, BitgetPublicWsClient.tickerChannel, symbol));
		}
		return new WsRequest("unsubscribe", args).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getSubscribeBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	private void sendPing() {
		sendMessage("ping");
	}

	@Override
	public void close() {
		super.close();
		pingExecutor.close();
	}
}
