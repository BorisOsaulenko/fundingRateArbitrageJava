package com.boris.fundingarbitrage.exchange.impl.bybit.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

record AuthRequest(String op, Object[] args) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
