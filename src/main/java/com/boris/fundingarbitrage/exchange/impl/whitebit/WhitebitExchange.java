package com.boris.fundingarbitrage.exchange.impl.whitebit;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest.WhitebitPrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.privatews.WhitebitPrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicws.WhitebitPublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

import java.util.concurrent.CompletableFuture;

public class WhitebitExchange {
	public static BaseExchange create() {
		ExchangeName name = ExchangeName.WHITEBIT;
		WhitebitContext context = new WhitebitContext();
		WhitebitPublicHttpClient publicHttp = new WhitebitPublicHttpClient(context);
		WhitebitPublicWsClient publicWs = new WhitebitPublicWsClient(context, publicHttp);
		WhitebitPrivateHttpClient privateHttp = new WhitebitPrivateHttpClient(context);
		CompletableFuture<String> privateWsTokenFuture = privateHttp.fetchWebsocketToken();
		WhitebitPrivateWsClient privateWs = new WhitebitPrivateWsClient(context, privateWsTokenFuture);

		return new BaseExchange(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
