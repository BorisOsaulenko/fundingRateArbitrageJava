package com.boris.fundingarbitrage.exchange.impl.gate;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.gate.privaterest.GatePrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.gate.privatews.GatePrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.GatePublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.GatePublicWsClient;

public class GateExchange extends BaseExchange {
	private static final GateContext context = new GateContext();
	private static final GatePrivateWsClient privateWs = new GatePrivateWsClient(context);
	private static final GatePublicHttpClient publicHttp = new GatePublicHttpClient(context);
	private static final GatePublicMessageHandler publicMessageHandler = new GatePublicMessageHandler(context);
	private static final GatePublicWsClient publicWs = new GatePublicWsClient(context, publicMessageHandler, publicHttp);
	private static final GatePrivateHttpClient privateHttp = new GatePrivateHttpClient(context);

	public GateExchange() {
		super(publicWs, privateWs, publicHttp, privateHttp);
	}
}
