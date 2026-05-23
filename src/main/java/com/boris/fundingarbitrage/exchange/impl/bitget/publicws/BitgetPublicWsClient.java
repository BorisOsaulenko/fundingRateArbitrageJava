package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;

public class BitgetPublicWsClient extends FullFundingViaRest {
	public BitgetPublicWsClient(BitgetContext context, BitgetPublicHttpClient publicHttp) {
		MessageHandler messageHandler = new MessageHandler(context);
		IPublicWsFrames wsFrames = new WsFrames();
		ClientsConfig config = new ClientsConfig(
						URI.create("wss://ws.bitget.com/v2/ws/public"),
						URI.create("wss://ws.bitget.com/v2/ws/public"),
						5,
						5,
						50,
						50,
						30,
						30
		);
		super(context, config, wsFrames, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
	}
}
