package com.boris.fundingarbitrage.exchange.impl.kucoin.ws.pojos;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import lombok.SneakyThrows;

public record WsRequest(
				String id, String type, String topic, boolean privateChannel, boolean response
) {
	@SneakyThrows
	public String toJson() {
		return ObjectMapperSingleton.getInstance().writeValueAsString(this);
	}
}
