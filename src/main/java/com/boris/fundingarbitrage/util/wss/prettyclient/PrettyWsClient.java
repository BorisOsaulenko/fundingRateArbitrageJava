package com.boris.fundingarbitrage.util.wss.prettyclient;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.util.logger.Logger;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;
import lombok.NonNull;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;

import java.io.IOException;
import java.net.URI;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class PrettyWsClient {
	private final URI endpointUri;
	private final PrettyWsReconnectHandler reconnectHandler;
	private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
	private final PrettyWsEndpoint endpoint;
	private final int maxReconnectAttempts = 5;
	private final ClientManager client;
	private boolean closeRequested = false;
	private CompletableFuture<Session> connecting = new CompletableFuture<>();
	private Session lastSession = null;
	private Consumer<Session> customOnOpenHook = null;
	private Consumer<Session> customOnCloseHook = null;

	public PrettyWsClient(
					@NonNull URI uri,
					@NonNull Consumer<String> processMessage,
					Consumer<Session> customOnOpenHook,
					Consumer<Session> customOnCloseHook
	) {
		this.customOnOpenHook = customOnOpenHook;
		this.customOnCloseHook = customOnCloseHook;
		this.endpointUri = uri;

		client = ClientManager.createClient();
		reconnectHandler = new PrettyWsReconnectHandler(endpointUri, maxReconnectAttempts, () -> !closeRequested);
		client.getProperties().put(ClientProperties.RECONNECT_HANDLER, reconnectHandler);
		this.endpoint = new PrettyWsEndpoint(processMessage, getOnOpenHook(), getOnCloseHook());
	}

	public PrettyWsClient(@NonNull URI uri, @NonNull Consumer<String> processMessage) {
		this(uri, processMessage, null, null);
	}

	public void connect() {
		try {
			client.connectToServer(this.endpoint, endpointUri);
		} catch (DeploymentException e) {
			Logger.error("Deployment exception. Wrong endpoint config: " + e.getMessage());
		} catch (IOException e) {
			Logger.error("Network/protocol issue: " + e.getMessage());
		}
	}

	public void sendMessage(String message) {
		if (message == null || message.isEmpty()) return;
		if (lastSession != null && lastSession.isOpen()) {
			lastSession.getAsyncRemote().sendText(
							message, result -> {
								if (!result.isOK()) {
									String msg = String.format("Failed to send WebSocket message: %s", result.getException().toString());
									Logger.error(msg);
								}
							}
			);
		} else {
			messageQueue.offer(message);
		}
	}

	public <T> void sendObject(T obj) {
		if (obj == null) return;
		try {
			String json = ObjectMapperSingleton.getInstance().writeValueAsString(obj);
			sendMessage(json);
		} catch (IOException e) {
			Logger.error("Failed to send object as JSON: " + e.getMessage());
		}
	}

	public void close() {
		closeRequested = true;

		if (lastSession != null && lastSession.isOpen()) {
			try {
				lastSession.close();
			} catch (IOException e) {
				Logger.error("Error closing WebSocket session: " + e.getMessage());
			}
		}

		connecting.completeExceptionally(new IllegalStateException("Client closed"));
	}

	public CompletableFuture<Session> connecting() {
		return connecting;
	}

	private Consumer<Session> getOnOpenHook() {
		return session -> {
			if (customOnOpenHook != null) customOnOpenHook.accept(session);

			lastSession = session;
			connecting.complete(session);
			reconnectHandler.reset();

			// Send queued messages
			while (!messageQueue.isEmpty()) {
				String msg = messageQueue.poll();
				if (msg != null) {
					sendMessage(msg);
				}
			}

			Logger.log("WebSocket connection established: " + endpointUri);
		};
	}

	private Consumer<Session> getOnCloseHook() {
		return session -> {
			if (customOnCloseHook != null) customOnCloseHook.accept(session);

			if (closeRequested) {
				Logger.log("WebSocket closed normally as requested.");
				lastSession = null;
				connecting.completeExceptionally(new IllegalStateException("Client closed"));
				return;
			}

			lastSession = null;
			connecting = new CompletableFuture<>();
		};
	}
}
