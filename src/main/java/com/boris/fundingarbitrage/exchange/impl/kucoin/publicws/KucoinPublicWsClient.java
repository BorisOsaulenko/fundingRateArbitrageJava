package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class KucoinPublicWsClient extends FullFundingViaRest {
	public KucoinPublicWsClient(ExchangeContext context, KucoinPublicHttpClient publicHttp) {
		MessageHandler messageHandler = new MessageHandler(context);
		CompletableFuture<URI> spotEndpointFuture = publicHttp.fetchPublicSpotToken();
		CompletableFuture<URI> futuresEndpointFuture = publicHttp.fetchPublicFuturesToken();
		IPublicWsFrames wsFrames = new WsFrames();

		ClientsConfig config = new ClientsConfig(
						spotEndpointFuture,
						futuresEndpointFuture,
						5,
						5,
						90,
						90,
						18,
						18
		);

		super(context, config, wsFrames, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
	}
}
