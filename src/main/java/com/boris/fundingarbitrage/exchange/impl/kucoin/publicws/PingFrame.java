package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

record PingFrame(
				String id,
				String type
) {
	private static final ObjectMapper mapper = ObjectMapperSingleton.getInstance();
	private static int IdIncrement = 0;

	PingFrame() {
		this(String.valueOf(IdIncrement++), "ping");
	}

	@SneakyThrows
	public String toJson() {
		return mapper.writeValueAsString(this);
	}
}
