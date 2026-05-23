package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import lombok.NonNull;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public record DomainClientConfig<T extends GenericPublicWsPatch>(
				@NonNull CompletableFuture<URI> endpoint,
				@NonNull OrchestratorConfig orchestratorConfig,
				@NonNull InstanceConfig<T> instanceConfig
) {
	public DomainClientConfig {
		if (orchestratorConfig.clientsAmount() <= 0)
			throw new RuntimeException("Amount of clients should be positive");
		if (orchestratorConfig.pingIntervalSeconds() < 0)
			throw new RuntimeException("Ping interval should be non-negative. Set to 0 for no ping.");
	}

	public DomainClientConfig(
					@NonNull URI endpoint,
					@NonNull OrchestratorConfig orchestratorConfig,
					@NonNull InstanceConfig<T> instanceConfig
	) {
		this(CompletableFuture.completedFuture(endpoint), orchestratorConfig, instanceConfig);
	}
}

