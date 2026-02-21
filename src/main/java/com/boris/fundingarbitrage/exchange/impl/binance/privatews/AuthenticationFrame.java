package com.boris.fundingarbitrage.exchange.impl.binance.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

import java.util.UUID;

record AuthenticationFrame(String id, String method, AuthParams params) {
	public AuthenticationFrame(String apiKey, String signature) {
		this(UUID.randomUUID().toString(), "session.logon", new AuthParams(apiKey, signature, System.currentTimeMillis()));
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
