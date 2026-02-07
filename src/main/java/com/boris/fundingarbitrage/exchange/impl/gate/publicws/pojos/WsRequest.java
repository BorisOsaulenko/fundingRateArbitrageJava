package com.boris.fundingarbitrage.exchange.impl.gate.publicws.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

public record WsRequest(long time, String channel, String event, String[] payload) {
	public WsRequest(String channel, String event, String[] payload) {
		this(System.currentTimeMillis() / 1000, channel, event, payload);
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
