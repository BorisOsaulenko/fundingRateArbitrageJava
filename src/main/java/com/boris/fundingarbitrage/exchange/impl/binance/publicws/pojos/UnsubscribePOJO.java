package com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos;

import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;

public class UnsubscribePOJO {
	public String method = "UNSUBSCRIBE";
	public String[] params;
	public int id = BinancePublicWsClient.getNextId();

	public UnsubscribePOJO(String[] params) {
		if (params == null || params.length == 0) {
			throw new IllegalArgumentException("Params cannot be null or empty");
		}

		this.params = params;
	}
}
