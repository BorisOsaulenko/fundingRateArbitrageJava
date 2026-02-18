package com.boris.fundingarbitrage.exchange.impl.whitebit;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest.WhitebitPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privatews.WhitebitPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicws.WhitebitPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

import java.util.concurrent.CompletableFuture;

public class WhitebitExchange extends BaseExchange {
	private static final ExchangeName name = ExchangeName.WHITEBIT;
	private static final WhitebitContext context = new WhitebitContext();
	private static final WhitebitPublicHttpClient publicHttp = new WhitebitPublicHttpClient(context);
	private static final WhitebitPublicWsClient publicWs = new WhitebitPublicWsClient(context, publicHttp);
	private static final WhitebitPrivateHttpClient privateHttp = new WhitebitPrivateHttpClient(context);
	private static final CompletableFuture<String> privateWsTokenFuture = privateHttp.fetchWebsocketToken();
	private static final WhitebitPrivateWsClient privateWs = new WhitebitPrivateWsClient(context, privateWsTokenFuture);

	public WhitebitExchange() {
		super(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
