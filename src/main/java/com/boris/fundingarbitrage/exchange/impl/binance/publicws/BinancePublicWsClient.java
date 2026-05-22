package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;

import java.net.URI;

public class BinancePublicWsClient extends PublicWsClient {
	public BinancePublicWsClient(ExchangeContext context) {
		URI futuresEndpoint = URI.create("wss://ws-fapi.binance.com/ws-fapi/v1");
		URI spotEndpoint = URI.create("wss://stream.binance.com:9443/ws");
		MessageHandler messageHandler = new MessageHandler(context);
		IPublicWsFrames wsFrames = new WsFrames();
		ClientsConfig config = new ClientsConfig(
						spotEndpoint,
						futuresEndpoint,
						5,
						5,
						50,
						50,
						0,
						0
		);
		super(context, config, wsFrames, messageHandler, new ProdModifiableSchedulerBuilder());
	}
}
