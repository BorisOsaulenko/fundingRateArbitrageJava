package com.boris.fundingarbitrage.exchange.impl.okx.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

record LoginRequest(String op, Arg[] args) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}

	public record Arg(String apiKey, String passphrase, String timestamp, String sign) {}
}
