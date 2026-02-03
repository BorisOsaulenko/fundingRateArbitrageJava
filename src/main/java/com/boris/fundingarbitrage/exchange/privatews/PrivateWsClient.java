package com.boris.fundingarbitrage.exchange.privatews;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClient;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClientBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public abstract class PrivateWsClient {
	protected final PrettyWsClient prettyWsClient;
	protected final ExchangeContext exchangeContext;
	private final Set<Consumer<DepositPatch>> depositHandlers = new HashSet<>();
	private final Map<String, Consumer<PartialFill>> partialFillHandlers = new HashMap<>();
	private final PrivateMessageHandler messageHandler;

	public PrivateWsClient(
					ExchangeContext context,
					URI endpoint,
					PrivateMessageHandler messageHandler
	) {
		this.exchangeContext = context;
		this.messageHandler = messageHandler;
		this.prettyWsClient = new PrettyWsClientBuilder(endpoint, this::handleMessage)
						.withOnOpenHook((s) -> CompletableFuture.runAsync(this::sendAuthenticationFrame))
						.build();
	}

	public final void close() {
		this.prettyWsClient.close();
	}

	protected abstract void sendSubscribeDepositFrame();

	protected abstract void sendUnsubscribeDepositFrame();

	protected abstract void sendSubscribePartialFillsFrame();

	protected abstract void sendUnsubscribePartialFillsFrame();

	protected abstract void sendAuthenticationFrame();

	public void subscribeDeposits(Consumer<DepositPatch> handler) {
		boolean wasEmpty = depositHandlers.isEmpty();
		depositHandlers.add(handler);
		if (wasEmpty) sendSubscribeDepositFrame();
	}

	public void unsubscribeDeposits(Consumer<DepositPatch> handler) {
		depositHandlers.remove(handler);
		if (depositHandlers.isEmpty()) sendUnsubscribeDepositFrame();
	}

	public void subscribePartialFills(String clientOrderId, Consumer<PartialFill> handler) {
		boolean wasEmpty = partialFillHandlers.isEmpty();
		partialFillHandlers.put(clientOrderId, handler);
		if (wasEmpty) sendSubscribePartialFillsFrame();
	}

	public void unsubscribePartialFills(String clientOrderId) {
		partialFillHandlers.remove(clientOrderId);
		if (partialFillHandlers.isEmpty()) sendUnsubscribePartialFillsFrame();
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
			prettyWsClient.sendMessage(pingResponse);
		}
	}
}
