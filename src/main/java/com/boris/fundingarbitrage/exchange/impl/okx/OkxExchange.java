package com.boris.fundingarbitrage.exchange.impl.okx;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.okx.privaterest.OkxPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.okx.privatews.OkxPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.okx.publicws.OkxPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.okx.publicws.OkxPublicWsClient;

public class OkxExchange extends BaseExchange {
	private static final OkxContext context = new OkxContext();

	private static final OkxPrivateWsClient privateWs = new OkxPrivateWsClient(context);
	private static final OkxPublicHttpClient publicHttp = new OkxPublicHttpClient(context);
	private static final OkxPublicMessageHandler publicMessageHandler = new OkxPublicMessageHandler(context);
	private static final OkxPublicWsClient publicWs = new OkxPublicWsClient(
					context,
					publicMessageHandler,
					publicHttp
	);
	private static final OkxPrivateHttpClient privateHttp = new OkxPrivateHttpClient(context);

	public OkxExchange() {
		super(publicWs, privateWs, publicHttp, privateHttp);
	}
}
