package com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.impl.binance.publicws.BinancePublicWsClient;
import lombok.SneakyThrows;

public record WsRequest(String method, String[] params, int id) {
	public WsRequest(String method, String[] params) {
		this(method, params, BinancePublicWsClient.getNextId());
	}

	public WsRequest {
		if (params == null || params.length == 0) {
			throw new IllegalArgumentException("Params cannot be null or empty");
		}
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}