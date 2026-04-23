package com.boris.fundingarbitrage.exchange.impl.binance;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.impl.binance.privaterest.BinancePrivateHttpClient;
import com.boris.fundingarbitrage.exchange.impl.binance.privatews.BinancePrivateWsClient;
import com.boris.fundingarbitrage.exchange.impl.binance.publicrest.BinancePublicHttpClient;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public final class BinanceExchange {
	public static BaseExchange create() {
		BinanceContext context = new BinanceContext();
		var privateWs = new BinancePrivateWsClient(context);
		var publicHttp = new BinancePublicHttpClient(context);
		var publicWs = new BinancePublicWsClient(context, publicHttp);
		var privateHttp = new BinancePrivateHttpClient(context);
		return new BaseExchange(ExchangeName.BINANCE, publicWs, privateWs, publicHttp, privateHttp);
	}
}
