package com.boris.fundingarbitrage.exchange.impl.okx.privatews.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.SneakyThrows;

public record WsRequest(String op, Arg[] args) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Arg(String channel, String instType) {}
}
