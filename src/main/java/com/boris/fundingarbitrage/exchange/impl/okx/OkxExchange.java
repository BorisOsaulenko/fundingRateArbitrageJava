package com.boris.fundingarbitrage.exchange.impl.okx;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.okx.privaterest.OkxPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.okx.publicws.OkxPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public class OkxExchange {
	public static BaseExchange create() {
		ExchangeName name = ExchangeName.OKX;
		OkxContext context = new OkxContext();

		OkxPublicHttpClient publicHttp = new OkxPublicHttpClient(context);
		OkxPublicWsClient publicWs = new OkxPublicWsClient(context, publicHttp);
		OkxPrivateHttpClient privateHttp = new OkxPrivateHttpClient(context);

		return new BaseExchange(name, publicWs, publicHttp, privateHttp);
	}
}
