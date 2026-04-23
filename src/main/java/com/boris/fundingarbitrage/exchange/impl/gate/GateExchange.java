package com.boris.fundingarbitrage.exchange.impl.gate;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.gate.privaterest.GatePrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.gate.privatews.GatePrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.gate.publicws.GatePublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public class GateExchange {
	public static BaseExchange create() {
		ExchangeName name = ExchangeName.GATE;
		GateContext context = new GateContext();
		GatePrivateWsClient privateWs = new GatePrivateWsClient(context);
		GatePublicHttpClient publicHttp = new GatePublicHttpClient(context);
		GatePublicWsClient publicWs = new GatePublicWsClient(context, publicHttp);
		GatePrivateHttpClient privateHttp = new GatePrivateHttpClient(context);

		return new BaseExchange(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
