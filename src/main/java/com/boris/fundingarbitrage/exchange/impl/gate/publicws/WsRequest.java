package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

import java.util.Set;

record WsRequest(long time, String channel, String event, Set<String> payload) {
	public WsRequest(String channel, String event, Set<String> payload) {
		this(System.currentTimeMillis() / 1000, channel, event, payload);
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
