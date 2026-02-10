package com.boris.fundingarbitrage.exchange.impl.okx.publicws.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

public record WsRequest(String op, Arg[] args) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}

	public record Arg(String channel, String instId) {}
}
