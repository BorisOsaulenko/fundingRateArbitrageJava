package com.boris.fundingarbitrage.exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest.KucoinPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.privatews.KucoinPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicws.KucoinPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public class KucoinExchange extends BaseExchange {
	private static final ExchangeName name = ExchangeName.KUCOIN;
	private static final KucoinContext context = new KucoinContext();
	
	private static final KucoinPublicHttpClient publicHttp = new KucoinPublicHttpClient(context);
	private static final KucoinPublicWsClient publicWs = new KucoinPublicWsClient(context, publicHttp);
	private static final KucoinPrivateHttpClient privateHttp = new KucoinPrivateHttpClient(context);
	private static final KucoinPrivateWsClient privateWs = new KucoinPrivateWsClient(
					context,
					privateHttp.fetchPrivateWsEndpoint()
	);

	public KucoinExchange() {
		super(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
