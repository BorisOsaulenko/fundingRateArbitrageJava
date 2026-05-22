package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FundingSettlementViaRest;

import java.net.URI;

public class GatePublicWsClient extends FundingSettlementViaRest {
	public GatePublicWsClient(ExchangeContext context, GatePublicHttpClient publicHttp) {
		URI futuresEndpoint = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt");
		URI spotEndpoint = URI.create("wss://api.gateio.ws/ws/v4/");
		IPublicWsFrames wsFrames = new WsFrames();
		MessageHandler messageHandler = new MessageHandler(context);
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
		super(context, config, wsFrames, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
	}
}
