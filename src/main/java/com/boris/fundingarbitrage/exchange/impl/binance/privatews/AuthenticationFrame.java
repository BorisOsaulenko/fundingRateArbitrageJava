package com.boris.fundingarbitrage.exchange.impl.binance.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

import java.util.UUID;

record Params(String apiKey, String signature, long timestamp) {}

public record AuthenticationFrame(String id, String method, Params params) {
	public AuthenticationFrame(String apiKey, String signature) {
		this(UUID.randomUUID().toString(), "session.logon", new Params(apiKey, signature, System.currentTimeMillis()));
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
