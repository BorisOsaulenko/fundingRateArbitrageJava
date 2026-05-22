package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

record WsRequest(String method, String[] params, int id) {
	private static int idIncrement = 0;

	public WsRequest(String method, String[] params) {
		this(method, params, idIncrement++);
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