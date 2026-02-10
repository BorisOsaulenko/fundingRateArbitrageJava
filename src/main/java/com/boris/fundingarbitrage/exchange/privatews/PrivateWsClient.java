package com.boris.fundingarbitrage.exchange.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClient;
import jakarta.websocket.Session;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public abstract class PrivateWsClient {
	protected final ExchangeContext exchangeContext;
	private final CompletableFuture<PrettyWsClient> prettyWsClientFuture;
	private final Set<Consumer<DepositPatch>> depositHandlers = new HashSet<>();
	private final Map<String, Consumer<PartialFill>> partialFillHandlers = new HashMap<>();
	private final PrivateMessageHandler messageHandler;

	public PrivateWsClient(ExchangeContext context, URI endpoint, PrivateMessageHandler messageHandler) {
		this.exchangeContext = context;
		this.messageHandler = messageHandler;
		this.prettyWsClientFuture = CompletableFuture.completedFuture(new PrettyWsClient(
						endpoint,
						this::handleMessage,
						this::onConnect,
						null
		));
	}

	public PrivateWsClient(
					ExchangeContext context,
					CompletableFuture<URI> endpointFuture,
					PrivateMessageHandler messageHandler
	) {
		this.exchangeContext = context;
		this.messageHandler = messageHandler;
		this.prettyWsClientFuture = endpointFuture.thenApply(endpoint -> new PrettyWsClient(
						endpoint,
						this::handleMessage,
						this::onConnect,
						null
		));
		this.prettyWsClientFuture.thenAccept(PrettyWsClient::connect);
	}

	public final void close() {
		this.prettyWsClientFuture.thenAccept(PrettyWsClient::close);
	}

	protected abstract String getSubscribeDepositFrame();

	protected abstract String getUnsubscribeDepositFrame();

	protected abstract String getSubscribePartialFillsFrame();

	protected abstract String getUnsubscribePartialFillsFrame();

	protected abstract String getAuthenticationFrame();

	public void sendMessage(String message) {
		this.prettyWsClientFuture.thenAccept(client -> client.sendMessage(message));
	}

	public void sendObject(Object obj) {
		this.prettyWsClientFuture.thenAccept(client -> client.sendObject(obj));
	}

	public void subscribeDeposits(Consumer<DepositPatch> handler) {
		boolean wasEmpty = depositHandlers.isEmpty();
		depositHandlers.add(handler);
		if (wasEmpty) this.sendMessage(getSubscribeDepositFrame());
	}

	public void unsubscribeDeposits(Consumer<DepositPatch> handler) {
		depositHandlers.remove(handler);
		if (depositHandlers.isEmpty()) this.sendMessage(getUnsubscribeDepositFrame());
	}

	public void subscribePartialFills(String clientOrderId, Consumer<PartialFill> handler) {
		boolean wasEmpty = partialFillHandlers.isEmpty();
		partialFillHandlers.put(clientOrderId, handler);
		if (wasEmpty) this.sendMessage(getSubscribePartialFillsFrame());
	}

	public void unsubscribePartialFills(String clientOrderId) {
		partialFillHandlers.remove(clientOrderId);
		if (partialFillHandlers.isEmpty()) this.sendMessage(getUnsubscribePartialFillsFrame());
	}

	private void onConnect(Session session) {
		sendMessage(getAuthenticationFrame());
		if (!depositHandlers.isEmpty()) sendMessage(getSubscribeDepositFrame());
		if (!partialFillHandlers.isEmpty()) sendMessage(getSubscribePartialFillsFrame());
	}

	private void handleMessage(String message) {
		DepositPatch depositPatch = messageHandler.parseDepositMessageSymbol(message);
		if (depositPatch != null) {
			for (Consumer<DepositPatch> handler : depositHandlers) {
				handler.accept(depositPatch);
			}
			return;
		}

		PartialFill partialFill = messageHandler.parsePartialFillMessageSymbol(message);
		if (partialFill != null) {
			Consumer<PartialFill> handler = partialFillHandlers.get(partialFill.orderId());
			if (handler != null) {
				handler.accept(partialFill);
			}
			return;
		}

		String pingResponse = messageHandler.getResponseToPingMessage(message);
		if (pingResponse != null) {
			prettyWsClientFuture.thenAccept(client -> client.sendMessage(pingResponse));
		}
	}
}
