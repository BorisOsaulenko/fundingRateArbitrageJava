package com.boris.fundingarbitrage.exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.binance.privaterest.BinancePrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.binance.privatews.BinancePrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.binance.publicrest.BinancePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicMessageHandler;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public class BinanceExchange extends BaseExchange {
	private static final BinanceContext context = new BinanceContext();
	private static final ExchangeName name = ExchangeName.BINANCE;

	private static final BinancePrivateWsClient privateWs = new BinancePrivateWsClient(context);
	private static final BinancePublicHttpClient publicHttp = new BinancePublicHttpClient(context);
	private static final BinancePublicMessageHandler publicMessageHandler = new BinancePublicMessageHandler(context);
	private static final BinancePublicWsClient publicWs = new BinancePublicWsClient(
					context,
					publicMessageHandler,
					publicHttp
	);
	private static final BinancePrivateHttpClient privateHttp = new BinancePrivateHttpClient(context);

	public BinanceExchange() {
		super(name, publicWs, privateWs, publicHttp, privateHttp);
	}
}
