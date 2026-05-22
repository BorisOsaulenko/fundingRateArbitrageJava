package com.boris.fundingarbitrage.exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.binance.privaterest.BinancePrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.binance.publicrest.BinancePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public final class BinanceExchange {
	public static BaseExchange create() {
		BinanceContext context = new BinanceContext();
		var publicHttp = new BinancePublicHttpClient(context);
		var publicWs = new BinancePublicWsClient(context);
		var privateHttp = new BinancePrivateHttpClient(context);
		return new BaseExchange(ExchangeName.BINANCE, publicWs, publicHttp, privateHttp);
	}
}
