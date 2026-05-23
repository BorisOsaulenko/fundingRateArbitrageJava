package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public record InstanceConfig<T extends GenericPublicWsPatch>(
				int maxCoinSize,
				Function<JsonNode, T> parser,
				Supplier<String> getPingFrame,

				Function<Set<String>, String> getSubscribeFrame,

				Function<Set<String>, String> getUnsubscribeFrame
) {
}
