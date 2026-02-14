package com.boris.fundingarbitrage.exchange.impl.gate.privatews;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

public record WsRequest(long time, String channel, String event, String[] payload, Auth auth) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}

	public record Auth(String method, String KEY, String SIGN) {}
}
