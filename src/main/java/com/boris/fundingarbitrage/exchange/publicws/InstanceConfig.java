package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Function;

public record InstanceConfig<T extends GenericPublicWsPatch>(
				int maxCoinSize,
				Function<JsonNode, T> parser,
				IInstanceWsFrames wsFrames
) {
}
