package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

import java.time.Instant;

record PingFrame(String id, String type) {
	public PingFrame() {
		this(String.valueOf(Instant.now().toEpochMilli()), "ping");
	}

	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
