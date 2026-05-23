package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class DomainClientConfigBuilder<T extends GenericPublicWsPatch> {
	private final DomainClientConfigPartial<T> config = new DomainClientConfigPartial<>();

	public DomainClientConfigBuilder(CompletableFuture<URI> endpoint) {
		config.endpoint = endpoint;
	}

	public DomainClientConfigBuilder(URI endpoint) {
		config.endpoint = CompletableFuture.completedFuture(endpoint);
	}

	public DomainClientConfigBuilder<T> orchestrator(int clientsAmount, int pingIntervalSeconds) {
		config.orchestratorConfig = new OrchestratorConfig(clientsAmount, pingIntervalSeconds);
		return this;
	}

	public DomainClientConfigBuilder<T> instanceConfig(
					int maxCoinSize,
					Function<JsonNode, T> parser
	) {
		config.instanceConfig = new InstanceConfigPartial<T>();
		config.instanceConfig.maxCoinSize = maxCoinSize;
		config.instanceConfig.parser = parser;
		return this;
	}

	public DomainClientConfigBuilder<T> frames(
					Supplier<String> pingFrame,
					Function<Set<String>, String> subscribeFrame,
					Function<Set<String>, String> unsubscribeFrame
	) {
		config.instanceConfig.pingFrame = pingFrame;
		config.instanceConfig.subscribeFrame = subscribeFrame;
		config.instanceConfig.unsubscribeFrame = unsubscribeFrame;
		return this;
	}

	public DomainClientConfigBuilder<T> frames(
					Function<Set<String>, String> subscribeFrame,
					Function<Set<String>, String> unsubscribeFrame
	) {
		config.instanceConfig.pingFrame = () -> null;
		config.instanceConfig.subscribeFrame = subscribeFrame;
		config.instanceConfig.unsubscribeFrame = unsubscribeFrame;
		return this;
	}

	public DomainClientConfig<T> build() {
		return config.assemble();
	}

	private static class DomainClientConfigPartial<T extends GenericPublicWsPatch> {
		CompletableFuture<URI> endpoint;
		OrchestratorConfig orchestratorConfig;
		InstanceConfigPartial<T> instanceConfig;

		DomainClientConfig<T> assemble() {
			if (endpoint == null || orchestratorConfig == null)
				throw new RuntimeException("Cant assemble non-full DomainClientConfig.");
			InstanceConfig<T> instanceConfig = this.instanceConfig.assemble();
			return new DomainClientConfig<T>(endpoint, orchestratorConfig, instanceConfig);
		}
	}

	private static class InstanceConfigPartial<T extends GenericPublicWsPatch> {
		int maxCoinSize = 0;
		Function<JsonNode, T> parser;
		Supplier<String> pingFrame;
		Function<Set<String>, String> subscribeFrame;
		Function<Set<String>, String> unsubscribeFrame;

		InstanceConfig<T> assemble() {
			if (maxCoinSize == 0 || pingFrame == null) throw new RuntimeException("Cant assemble non-full InstanceConfig.");
			return new InstanceConfig<T>(maxCoinSize, parser, pingFrame, subscribeFrame, unsubscribeFrame);
		}
	}
}
