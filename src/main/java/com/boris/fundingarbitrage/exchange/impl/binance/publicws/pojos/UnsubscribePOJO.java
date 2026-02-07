package com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import lombok.SneakyThrows;

public record UnsubscribePOJO(String method, String[] params, int id) {
	public UnsubscribePOJO(String[] params) {
		this("UNSUBSCRIBE", params, BinancePublicWsClient.getNextId());
	}

	public UnsubscribePOJO {
		if (params == null || params.length == 0) {
			throw new IllegalArgumentException("Params cannot be null or empty");
		}

		if (method == null || method.isBlank()) {
			method = "UNSUBSCRIBE";
		}
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}