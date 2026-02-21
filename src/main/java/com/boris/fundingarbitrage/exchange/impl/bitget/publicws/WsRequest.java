package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

import java.util.List;

record WsRequest(String op, List<Arg> args) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}

	public record Arg(String instType, String channel, String instId) {}
}
