package com.boris.fundingarbitrage.exchange.impl.whitebit.ws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;

public record WsRequest(long id, String method, List<Object> params) {
	public String toJson() {
		try {
			return ObjectMapperSingleton.getInstance().writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize WS request", e);
		}
	}
}
