package com.boris.fundingarbitrage.exchange.impl.bybit;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.bybit.privaterest.BybitPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.privatews.BybitPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicws.BybitPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public class BybitExchange {
	public static BaseExchange create() {
		ExchangeName name = ExchangeName.BYBIT;
		BybitContext context = new BybitContext();
		BybitPrivateWsClient privateWs = new BybitPrivateWsClient(context);
		BybitPublicHttpClient publicHttp = new BybitPublicHttpClient(context);
		BybitPublicWsClient publicWs = new BybitPublicWsClient(context, publicHttp);
		BybitPrivateHttpClient privateHttp = new BybitPrivateHttpClient(context);

		return new BaseExchange(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
