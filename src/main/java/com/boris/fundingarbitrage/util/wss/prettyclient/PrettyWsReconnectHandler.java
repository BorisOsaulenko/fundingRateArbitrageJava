package com.boris.fundingarbitrage.util.wss.prettyclient;

import com.boris.fundingarbitrage.util.logger.Logger;
import jakarta.websocket.CloseReason;
import lombok.NonNull;
import org.glassfish.tyrus.client.ClientManager;

import java.net.URI;
import java.util.function.BooleanSupplier;

public class PrettyWsReconnectHandler extends ClientManager.ReconnectHandler {
	private final URI endpointUri;
	private final int maxAttempts;
	private final BooleanSupplier allowReconnect; // e.g., () -> !closeRequested

	private int attempts = 0;

	public PrettyWsReconnectHandler(
					@NonNull URI endpointUri,
					int maxAttempts,
					@NonNull BooleanSupplier allowReconnect
	) {
		this.endpointUri = endpointUri;
		this.maxAttempts = maxAttempts;
		this.allowReconnect = allowReconnect;
	}

	public void reset() {
		attempts = 0;
	}

	private boolean shouldReconnect() {
		attempts++;
		if (attempts >= maxAttempts) {
			Logger.error("Max reconnection attempts reached. Stopping reconnection.");
		}
		return allowReconnect.getAsBoolean() && attempts < maxAttempts;
	}

	@Override
	public boolean onDisconnect(CloseReason closeReason) {
		Logger.warn(String.format("%s on %s", closeReason, endpointUri));
		return shouldReconnect();
	}

	@Override
	public boolean onConnectFailure(Exception exception) {
		Logger.warn(String.format("%s on %s", exception, endpointUri));
		return shouldReconnect();
	}

	@Override
	public long getDelay() {
		// Keep your original backoff shape
		return 1000 + (long) Math.pow(5, attempts);
	}
}
