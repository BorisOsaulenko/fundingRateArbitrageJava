package com.boris.fundingarbitrage.exchange.impl.bybit.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

record WsRequest(String op, String[] args) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
