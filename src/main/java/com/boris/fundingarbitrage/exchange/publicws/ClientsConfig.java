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
				long spotPingIntervalSeconds,
				long futuresPingIntervalSeconds
) {
	public ClientsConfig {
		if (spotClientsAmount <= 0 || futuresClientsAmount <= 0)
			throw new RuntimeException("Amount of clients should be positive");
	}

	public ClientsConfig(
					URI spotEndpoint,
					URI futuresEndpoint,
					int spotClientsAmount,
					int futuresClientsAmount,
					int spotRequestMaxCoinSize,
					int futuresRequestMaxCoinSize,
					long spotPingIntervalSeconds,
					long futuresPingIntervalSeconds
	) {
		this(
						CompletableFuture.completedFuture(spotEndpoint),
						CompletableFuture.completedFuture(futuresEndpoint),
						spotClientsAmount,
						futuresClientsAmount,
						spotRequestMaxCoinSize,
						futuresRequestMaxCoinSize,
						spotPingIntervalSeconds,
						futuresPingIntervalSeconds
		);
	}

	public long spotPingIntervalMs() {
		return spotPingIntervalSeconds() * 1000L;
	}

	public long futuresPingIntervalMs() {
		return futuresPingIntervalSeconds() * 1000L;
	}
}
