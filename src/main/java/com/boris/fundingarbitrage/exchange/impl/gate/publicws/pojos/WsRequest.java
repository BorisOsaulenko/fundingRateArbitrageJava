package com.boris.fundingarbitrage.exchange.impl.gate.publicws.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

import java.util.List;

public record WsRequest(long time, String channel, String event, List<String> payload) {
	public WsRequest(String channel, String event, List<String> payload) {
		this(System.currentTimeMillis() / 1000, channel, event, payload);
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
