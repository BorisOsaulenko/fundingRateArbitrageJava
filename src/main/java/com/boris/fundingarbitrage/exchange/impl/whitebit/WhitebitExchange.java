package com.boris.fundingarbitrage.exchange.impl.whitebit;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest.WhitebitPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privatews.WhitebitPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicws.WhitebitPublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicws.WhitebitPublicWsClient;

public class WhitebitExchange extends BaseExchange {
	private static final WhitebitContext context = new WhitebitContext();
	private static final WhitebitPublicHttpClient publicHttp = new WhitebitPublicHttpClient(context);
	private static final WhitebitPublicMessageHandler publicMessageHandler = new WhitebitPublicMessageHandler(context);
	private static final WhitebitPublicWsClient publicWs = new WhitebitPublicWsClient(context, publicMessageHandler, publicHttp);
	private static final WhitebitPrivateWsClient privateWs = new WhitebitPrivateWsClient(context);
	private static final WhitebitPrivateHttpClient privateHttp = new WhitebitPrivateHttpClient(context);

	public WhitebitExchange() {
		super(publicWs, privateWs, publicHttp, privateHttp);
	}
}
