package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

public record PingFrame(
				int req_id,
				String op
) {
	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final int idIncrement = 0;

	public PingFrame() {
		this(idIncrement, "ping");
	}

	@SneakyThrows
	public String toJson() {
		return objectMapper.writeValueAsString(this);
	}
}
