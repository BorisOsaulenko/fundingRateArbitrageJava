package com.boris.fundingarbitrage.exchange.publicws;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public record ClientsConfig(
				CompletableFuture<URI> spotEndpointFuture,
				CompletableFuture<URI> futuresEndpointFuture,
				int spotClientsAmount,
				int futuresClientsAmount,
				int spotRequestMaxCoinSize,
				int futuresRequestMaxCoinSize,
				long spotPingIntervalMs,
				long futuresPingIntervalMs
) {
	public ClientsConfig {
		if (spotClientsAmount <= 0 || futuresClientsAmount <= 0)
			throw new RuntimeException("Amount of clients should be positive");
	}
}
