package com.boris.fundingarbitrage.exchange.impl.kucoin;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.kucoin.privaterest.KucoinPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.privatews.KucoinPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicws.KucoinPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicws.KucoinPublicWsClient;

public class KucoinExchange extends BaseExchange {
	private static final KucoinContext context = new KucoinContext();
	private static final KucoinPublicHttpClient publicHttp = new KucoinPublicHttpClient(context);
	private static final KucoinPublicMessageHandler publicMessageHandler = new KucoinPublicMessageHandler(context);
	private static final KucoinPublicWsClient publicWs = new KucoinPublicWsClient(
					context,
					publicMessageHandler,
					publicHttp
	);
	private static final KucoinPrivateHttpClient privateHttp = new KucoinPrivateHttpClient(context);
	private static final KucoinPrivateWsClient privateWs = new KucoinPrivateWsClient(
					context,
					privateHttp.fetchPrivateWsEndpoint()
	);

	public KucoinExchange() {
		super(publicWs, privateWs, publicHttp, privateHttp);
	}
}
