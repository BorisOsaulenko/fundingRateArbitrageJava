package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;

public class BybitPublicWsClient extends FullFundingViaRest {
	public BybitPublicWsClient(ExchangeContext context, BybitPublicHttpClient publicHttp) {
		URI futuresEndpoint = URI.create("wss://stream.bybit.com/v5/public/linear");
		URI spotEndpoint = URI.create("wss://stream.bybit.com/v5/public/spot");
		MessageHandler messageHandler = new MessageHandler(context);
		IPublicWsFrames wsFrames = new WsFrames();
		ClientsConfig config = new ClientsConfig(
						spotEndpoint,
						futuresEndpoint,
						20,
						5,
						10,
						50,
						20,
						20
		);

		super(context, config, wsFrames, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
	}
}
