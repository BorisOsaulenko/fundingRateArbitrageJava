package com.boris.fundingarbitrage.exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest.KucoinPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.privatews.KucoinPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicws.KucoinPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public class KucoinExchange {
	public static BaseExchange create() {
		ExchangeName name = ExchangeName.KUCOIN;
		KucoinContext context = new KucoinContext();

		KucoinPublicHttpClient publicHttp = new KucoinPublicHttpClient(context);
		KucoinPublicWsClient publicWs = new KucoinPublicWsClient(context, publicHttp);
		KucoinPrivateHttpClient privateHttp = new KucoinPrivateHttpClient(context);
		KucoinPrivateWsClient privateWs = new KucoinPrivateWsClient(context, privateHttp.fetchPrivateWsEndpoint());

		return new BaseExchange(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
