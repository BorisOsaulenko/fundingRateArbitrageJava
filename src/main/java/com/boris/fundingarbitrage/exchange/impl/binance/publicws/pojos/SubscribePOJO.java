package com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import lombok.SneakyThrows;

public record SubscribePOJO(String method, String[] params, int id) {
	public SubscribePOJO(String[] params) {
		this("SUBSCRIBE", params, BinancePublicWsClient.getNextId());
	}

	public SubscribePOJO {
		if (params == null || params.length == 0) {
			throw new IllegalArgumentException("Params cannot be null or empty");
		}

		if (method == null || method.isBlank()) {
			method = "SUBSCRIBE";
		}
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}