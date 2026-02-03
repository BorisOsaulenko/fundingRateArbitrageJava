package com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos;

import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;

public class SubscribePOJO {
	public String method = "SUBSCRIBE";
	public String[] params;
	public int id = BinancePublicWsClient.getNextId();

	public SubscribePOJO(String[] params) {
		if (params == null || params.length == 0) {
			throw new IllegalArgumentException("Params cannot be null or empty");
		}

		this.params = params;
	}
}
