package com.boris.fundingarbitrage.exchange.publicws;

public record OrchestratorConfig(
				int clientsAmount,
				int pingIntervalSeconds
) {
	public long pingIntervalMs() {
		return pingIntervalSeconds * 1000L;
	}
}
