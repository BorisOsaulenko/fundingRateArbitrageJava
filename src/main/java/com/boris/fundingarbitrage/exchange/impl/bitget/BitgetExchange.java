package com.boris.fundingarbitrage.exchange.impl.bitget;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.bitget.privaterest.BitgetPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.privatews.BitgetPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.BitgetPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicws.BitgetPublicWsClient;

public class BitgetExchange extends BaseExchange {
	private static final BitgetContext context = new BitgetContext();
	private static final BitgetPrivateWsClient privateWs = new BitgetPrivateWsClient(context);
	private static final BitgetPublicHttpClient publicHttp = new BitgetPublicHttpClient(context);
	private static final BitgetPublicMessageHandler publicMessageHandler = new BitgetPublicMessageHandler(
					context,
					publicHttp
	);
	private static final BitgetPublicWsClient publicWs = new BitgetPublicWsClient(
					context,
					publicMessageHandler
	);
	private static final BitgetPrivateHttpClient privateHttp = new BitgetPrivateHttpClient(context);

	public BitgetExchange() {
		super(publicWs, privateWs, publicHttp, privateHttp);
	}
}
