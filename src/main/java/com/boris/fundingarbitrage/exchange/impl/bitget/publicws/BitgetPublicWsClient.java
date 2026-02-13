package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.pojos.WsRequest;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BitgetPublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://ws.bitget.com/v2/ws/public");
	private static final String instType = "USDT-FUTURES";
	private static final String tickerChannel = "ticker";
	private final ScheduledExecutorService pingExecutor = new ScheduledThreadPoolExecutor(1);

	public BitgetPublicWsClient(
					ExchangeContext context,
					BitgetPublicMessageHandler messageHandler,
					BitgetPublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
		pingExecutor.scheduleAtFixedRate(this::sendPing, 5, 30, TimeUnit.SECONDS);
	}

	private String getSubscribeFrame(List<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(instType, BitgetPublicWsClient.tickerChannel, symbol));
		}
		return new WsRequest("subscribe", args).toJson();
	}

	private String getUnsubscribeFrame(List<String> symbols) {
		ArrayList<WsRequest.Arg> args = new ArrayList<>();
		for (String symbol : symbols) {
			args.add(new WsRequest.Arg(instType, BitgetPublicWsClient.tickerChannel, symbol));
		}
		return new WsRequest("unsubscribe", args).toJson();
	}

	@Override
	protected String getSubscribeFundingRateFrame(List<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeFundingRateFrame(List<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	protected String getSubscribeBookTickerFrame(List<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeBookTickerFrame(List<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	protected String getSubscribeMarkPriceFrame(List<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	protected String getUnsubscribeMarkPriceFrame(List<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	private void sendPing() {
		sendMessage("ping");
	}
}
