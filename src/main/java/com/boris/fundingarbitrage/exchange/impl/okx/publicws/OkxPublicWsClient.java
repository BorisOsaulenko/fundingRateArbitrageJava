package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;

public class OkxPublicWsClient extends FullFundingViaRest {
	public OkxPublicWsClient(ExchangeContext context, OkxPublicHttpClient publicHttp) {
		URI endpoint = URI.create("wss://ws.okx.com:8443/ws/v5/public");
		MessageHandler messageHandler = new MessageHandler(context);
		IPublicWsFrames wsFrames = new WsFrames();
		ClientsConfig config = new ClientsConfig(
						endpoint,
						endpoint,
						1,
						1,
						300,
						300,
						30,
						30
		);
		super(context, config, wsFrames, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
	}
}
