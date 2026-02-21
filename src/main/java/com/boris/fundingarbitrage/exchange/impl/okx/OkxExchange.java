package com.boris.fundingarbitrage.exchange.impl.okx;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.okx.privatews.OkxPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.okx.publicws.OkxPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import impl.okx.privaterest.OkxPrivateHttpClient;

public class OkxExchange extends BaseExchange {
	private static final ExchangeName name = ExchangeName.OKX;
	private static final OkxContext context = new OkxContext();

	private static final OkxPrivateWsClient privateWs = new OkxPrivateWsClient(context);
	private static final OkxPublicHttpClient publicHttp = new OkxPublicHttpClient(context);
	private static final OkxPublicWsClient publicWs = new OkxPublicWsClient(context, publicHttp);
	private static final OkxPrivateHttpClient privateHttp = new OkxPrivateHttpClient(context);

	public OkxExchange() {
		super(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
