package com.boris.fundingarbitrage.exchange.impl.bybit;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.bybit.privaterest.BybitPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.privatews.BybitPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicws.BybitPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public class BybitExchange extends BaseExchange {
	private static final ExchangeName name = ExchangeName.BYBIT;
	private static final BybitContext context = new BybitContext();
	private static final BybitPrivateWsClient privateWs = new BybitPrivateWsClient(context);
	private static final BybitPublicHttpClient publicHttp = new BybitPublicHttpClient(context);
	private static final BybitPublicWsClient publicWs = new BybitPublicWsClient(context, publicHttp);
	private static final BybitPrivateHttpClient privateHttp = new BybitPrivateHttpClient(context);

	public BybitExchange() {
		super(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
