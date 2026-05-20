package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Set;

public class BitgetPublicWsClient extends FullFundingViaRest {
	private static final URI endpoint = URI.create("wss://ws.bitget.com/v2/ws/public");
	private static final String instType = "USDT-FUTURES";
	private static final String tickerChannel = "ticker";

	private static final ClientsConfig config = new ClientsConfig(
					URI.create("wss://ws.bitget.com/v2/ws/public"),
					URI.create("wss://ws.bitget.com/v2/ws/public"),
					5,
					5,
					50,
					50,


	);

	public BitgetPublicWsClient(BitgetContext context, BitgetPublicHttpClient publicHttp) {
		BitgetPublicMessageHandler messageHandler = new BitgetPublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
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
	public String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	public String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	public String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	public String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	public String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	public String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	public String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getSubscribeFrame(symbols);
	}

	@Override
	public String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeFrame(symbols);
	}

	@Override
	public String getSpotPingFrame() {
		return "ping";
	}
}
