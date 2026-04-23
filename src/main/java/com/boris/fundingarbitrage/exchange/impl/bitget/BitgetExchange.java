package com.boris.fundingarbitrage.exchange.impl.bitget;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.bitget.privaterest.BitgetPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.privatews.BitgetPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.BitgetPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public final class BitgetExchange {
	public static BaseExchange create() {
		ExchangeName name = ExchangeName.BITGET;
		BitgetContext context = new BitgetContext();

		BitgetPrivateWsClient privateWs = new BitgetPrivateWsClient(context);
		BitgetPublicHttpClient publicHttp = new BitgetPublicHttpClient(context);
		BitgetPublicWsClient publicWs = new BitgetPublicWsClient(context, publicHttp);
		BitgetPrivateHttpClient privateHttp = new BitgetPrivateHttpClient(context);

		return new BaseExchange(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
