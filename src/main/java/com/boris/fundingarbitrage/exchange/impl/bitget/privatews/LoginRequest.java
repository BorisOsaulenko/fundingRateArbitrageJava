package com.boris.fundingarbitrage.exchange.impl.bitget.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

record LoginRequest(String op, LoginArg[] args) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}

	public record LoginArg(String apiKey, String passphrase, String timestamp, String sign) {}
}
