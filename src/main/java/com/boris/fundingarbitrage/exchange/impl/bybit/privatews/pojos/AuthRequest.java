package com.boris.fundingarbitrage.exchange.impl.bybit.privatews.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

public record AuthRequest(String op, Object[] args) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
