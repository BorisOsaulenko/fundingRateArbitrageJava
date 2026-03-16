package com.boris.fundingarbitrage.util.wss.prettyclient;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.util.logger.Logger;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;
import lombok.NonNull;
import org.glassfish.tyrus.client.ClientManager;

import java.io.IOException;
import java.net.URI;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class PrettyWsClient {
	private final URI endpointUri;
	private final String endpointName;
	private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
	private final PrettyWsEndpoint endpoint;
	private final ClientManager client;
	private final ScheduledExecutorService reconnectScheduler;
	private final int maxReconnectAttempts = 5;
	private ScheduledFuture<?> reconnectFuture = null;
	private int reconnectAttempts = 0;
	private boolean closeRequested = false;
	private CompletableFuture<Session> connecting = new CompletableFuture<>();
	private Session lastSession = null;
	private Consumer<Session> customOnOpenHook = null;
	private Consumer<Session> customOnCloseHook = null;
	private Runnable customOnUnhandledDisconnectHook = null;

	public PrettyWsClient(
					@NonNull URI uri,
					@NonNull String endpointName,
					@NonNull Consumer<String> processMessage
	) {
		this.endpointUri = uri;
		this.endpointName = endpointName;
		this.client = ClientManager.createClient();
		this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread thread = new Thread(r, "pretty-ws-reconnect-" + endpointName);
			thread.setDaemon(true);
			return thread;
		});

		this.endpoint = new PrettyWsEndpoint(processMessage, getOnOpenHook(), getOnCloseHook(), getOnErrorHook());
	}

	public void onOpen(Consumer<Session> hook) {
		this.customOnOpenHook = hook;
	}

	public void onClose(Consumer<Session> hook) {
		this.customOnCloseHook = hook;
	}

	public void onUnhandledDisconnect(Runnable hook) {
		this.customOnUnhandledDisconnectHook = hook;
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
									String msg = String.format(
													"[%s] Failed to send WebSocket message: %s",
													endpointName,
													result.getException().toString()
									);
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
			Logger.error(String.format("[%s] Failed to send object as JSON: %s", endpointName, e.getMessage()));
		}
	}

	public void close() {
		closeRequested = true;
		cancelReconnect();

		if (lastSession != null && lastSession.isOpen()) {
			try {
				lastSession.close();
			} catch (IOException e) {
				Logger.error(String.format("[%s] Error closing WebSocket session: %s", endpointName, e.getMessage()));
			}
		}

		connecting.completeExceptionally(new IllegalStateException("Client closed"));
	}

	private Consumer<Session> getOnOpenHook() {
		return session -> {
			if (customOnOpenHook != null) customOnOpenHook.accept(session);

			lastSession = session;
			connecting.complete(session);
			resetReconnectState();

			// Send queued messages
			while (!messageQueue.isEmpty()) {
				String msg = messageQueue.poll();
				if (msg != null) {
					sendMessage(msg);
				}
			}

			Logger.log("WebSocket connection established: " + endpointName);
		};
	}

	private Consumer<Session> getOnCloseHook() {
		return session -> {
			if (customOnCloseHook != null) customOnCloseHook.accept(session);

			if (closeRequested) {
				Logger.log("WebSocket closed normally as requested: " + endpointName);
				lastSession = null;
				connecting.completeExceptionally(new IllegalStateException("Client closed"));
				return;
			}

			lastSession = null;
			connecting = new CompletableFuture<>();
			scheduleReconnect("unexpected close");
		};
	}

	private Consumer<Throwable> getOnErrorHook() {
		return throwable -> {
			if (closeRequested) return;
			if (lastSession != null && lastSession.isOpen()) return;
			connecting = new CompletableFuture<>();
			scheduleReconnect("error");
		};
	}

	private synchronized void scheduleReconnect(String reason) {
		if (reconnectAttempts + 1 >= maxReconnectAttempts) {
			Logger.error("Max reconnection attempts reached. Stopping reconnection.");
			if (customOnUnhandledDisconnectHook != null) customOnUnhandledDisconnectHook.run();
			return;
		}
		if (reconnectFuture != null && !reconnectFuture.isDone()) return;

		reconnectAttempts++;
		long delayMs = 1000 + (long) Math.pow(5, reconnectAttempts);
		Logger.warn(String.format(
						"Scheduling reconnect for %s in %d ms (attempt %d/%d)",
						reason,
						delayMs,
						reconnectAttempts,
						maxReconnectAttempts
		));
		reconnectFuture = reconnectScheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
	}

	private synchronized void resetReconnectState() {
		reconnectAttempts = 0;
		cancelReconnect();
	}

	private synchronized void cancelReconnect() {
		if (reconnectFuture != null) {
			reconnectFuture.cancel(false);
			reconnectFuture = null;
		}
	}

	public boolean isConnected() {
		return lastSession != null && lastSession.isOpen();
	}
}
