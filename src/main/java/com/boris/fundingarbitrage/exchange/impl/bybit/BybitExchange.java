package com.boris.fundingarbitrage.exchange.impl.bybit;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.bybit.privaterest.BybitPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.privatews.BybitPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicws.BybitPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicws.BybitPublicWsClient;

public class BybitExchange extends BaseExchange {
	private static final BybitContext context = new BybitContext();

	private static final BybitPublicMessageHandler publicMessageHandler = new BybitPublicMessageHandler(
					context);
	private static final BybitPublicWsClient publicWs = new BybitPublicWsClient(
					context,
					publicMessageHandler
	);

	private static final BybitPrivateWsClient privateWs = new BybitPrivateWsClient(context);
	private static final BybitPublicHttpClient publicHttp = new BybitPublicHttpClient(context);
	private static final BybitPrivateHttpClient privateHttp = new BybitPrivateHttpClient(context);

	public BybitExchange() {
		super(publicWs, privateWs, publicHttp, privateHttp);
	}
}
