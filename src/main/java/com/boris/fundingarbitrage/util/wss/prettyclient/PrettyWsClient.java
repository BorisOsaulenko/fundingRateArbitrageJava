package com.boris.fundingarbitrage.util.wss.prettyclient;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.scheduler.onetime.IOneTimeScheduler;
import com.boris.fundingarbitrage.scheduler.onetime.ProdOneTimeScheduler;
import jakarta.websocket.Session;
import lombok.NonNull;
import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PrettyWsClient {
	private final static Logger log = LoggerFactory.getLogger(PrettyWsClient.class);
	private final URI endpointUri;
	private final String endpointName;
	private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
	private final PrettyWsEndpoint endpoint;
	private final ClientManager client;
	private final IOneTimeScheduler reconnectScheduler;
	private volatile boolean reconnecting = false;
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
					@NonNull Consumer<String> processMessage,
					@NonNull IOneTimeScheduler scheduler
	) {
		this.endpointUri = uri;
		this.endpointName = endpointName;
		this.client = ClientManager.createClient();
		this.reconnectScheduler = scheduler;
		this.endpoint = new PrettyWsEndpoint(processMessage, getOnOpenHook(), getOnCloseHook(), getOnErrorHook());
	}

	public PrettyWsClient(
					@NonNull URI endpointUri,
					@NonNull String endpointName,
					@NonNull BiConsumer<String, PrettyWsClient> processMessage
	) {
		this.endpointUri = endpointUri;
		this.endpointName = endpointName;
		this.client = ClientManager.createClient();
		this.reconnectScheduler = new ProdOneTimeScheduler();
		this.endpoint = new PrettyWsEndpoint(
						(msg) -> processMessage.accept(msg, this),
						getOnOpenHook(),
						getOnCloseHook(),
						getOnErrorHook()
		);
	}

	public void onOpen(Consumer<Session> hook) {
		this.customOnOpenHook = hook;
	}

	public void onClose(Consumer<Session> hook) {
		this.customOnCloseHook = hook;
	}

	public void warnOnUnhandledDisconnect() {
		this.customOnUnhandledDisconnectHook = () -> log.warn(
						"[{}] WebSocket disconnected and will not reconnect.",
						endpointName
		);
	}

	public CompletableFuture<Void> connect() {
		connecting = CompletableFuture.supplyAsync(() -> {
			try {
				return client.asyncConnectToServer(endpoint, endpointUri).get();
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		});
		return connecting.thenApply(_ -> null);
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
									log.error(msg);
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
			log.error("[{}] Failed to send object as JSON: {}", endpointName, e.getMessage());
		}
	}

	public void close() {
		closeRequested = true;
		resetReconnectState();

		if (lastSession != null && lastSession.isOpen()) {
			try {
				lastSession.close();
			} catch (IOException e) {
				log.error("[{}] Error closing WebSocket session: {}", endpointName, e.getMessage());
			}
		}
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

			log.info("WebSocket connection established: {}", endpointName);
		};
	}

	private Consumer<Session> getOnCloseHook() {
		return session -> {
			if (customOnCloseHook != null) customOnCloseHook.accept(session);

			if (closeRequested) {
				log.info("WebSocket closed normally as requested: {}", endpointName);
				lastSession = null;
				connecting.completeExceptionally(new IllegalStateException("Client closed"));
				return;
			}

			lastSession = null;
			connecting = new CompletableFuture<>();
			log.warn("Websocket closed unexpectedly: {}.", endpointName);
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
		int maxReconnectAttempts = 5;
		if (reconnectAttempts > maxReconnectAttempts) {
			log.error("Max reconnection attempts reached. Stopping reconnection.");
			if (customOnUnhandledDisconnectHook != null) customOnUnhandledDisconnectHook.run();
			return;
		}
		if (reconnecting) return;

		reconnectAttempts++;
		long delayMs = 1000 + (long) Math.pow(5, reconnectAttempts);
		log.warn(
						"Scheduling reconnect for {} in {} ms (attempt {}/{})",
						reason,
						delayMs,
						reconnectAttempts,
						maxReconnectAttempts
		);
		reconnectScheduler.schedule(this::connect, delayMs);
	}

	private synchronized void resetReconnectState() {
		reconnectAttempts = 0;
		reconnectScheduler.cancelAll();
		reconnecting = false;
	}


	public boolean isConnected() {
		return lastSession != null && lastSession.isOpen();
	}
}
