package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.Session;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

class PublicWsInstance<T extends GenericPublicWsPatch> {
	private static final ObjectMapper JSON_MAPPER = ObjectMapperSingleton.getInstance();
	private final CoinVector<Consumer<T>> handlersMap = new CoinVector<>();
	private final PrettyWsClient client; // protected for custom tweaks in subclasses
	private final InstanceConfig<T> config;
	private final String name;
	private final Logger log = LoggerFactory.getLogger(PublicWsInstance.class);

	public PublicWsInstance(
					@NonNull URI endpoint,
					@NonNull InstanceConfig<T> config,
					@NonNull String name
	) {
		this.name = name;
		this.config = config;
		this.client = getClient(endpoint);
	}

	private PrettyWsClient getClient(URI endpoint) {
		var client = new PrettyWsClient(
						endpoint,
						"Public Ws Instance: " + this.name,
						this::handleMessage
		);
		client.onOpen(this::onConnect);
		return client;
	}

	public CompletableFuture<Void> connect() {
		return client.connect().exceptionally(ex -> {
			log.error("Failed to connect to {}: {}", name, ex.getMessage());
			throw new RuntimeException("Failed to connect to " + name, ex);
		});
	}

	public void close() {
		client.close();
	}

	public void sendPing() {
		client.sendMessage(config.getPingFrame().get());
	}

	private void sendChunks(Set<String> elems, int cap, Function<Set<String>, String> getMessage) {
		List<Set<String>> result = new ArrayList<>();
		Set<String> currentSet = new HashSet<>();

		for (String coin : elems) {
			if (currentSet.size() >= cap) {
				result.add(currentSet);
				currentSet = new HashSet<>();
			}
			currentSet.add(coin);
		}

		if (!currentSet.isEmpty()) result.add(currentSet);
		for (Set<String> chunk : result) client.sendMessage(getMessage.apply(chunk));
	}

	protected Set<String> addHandlers(
					Set<String> coins,
					Consumer<T> handler
	) {
		Set<String> addedCoins = new HashSet<>();
		for (String coin : coins) {
			if (!handlersMap.containsKey(coin)) addedCoins.add(coin);
			handlersMap.put(coin, handler);
		}

		return addedCoins;
	}

	protected Set<String> removeHandlers(
					Set<String> coins
	) {
		Set<String> removedCoins = new HashSet<>();
		for (String coin : coins) {
			if (handlersMap.remove(coin) != null) removedCoins.add(coin);
		}
		return removedCoins;
	}

	protected void sendDataFrame(
					Set<String> coins,
					Function<Set<String>, String> subscribeMessage
	) {
		if (coins.isEmpty()) return;
		sendChunks(coins, config.maxCoinSize(), subscribeMessage);
	}

	public void subscribe(Set<String> coins, Consumer<@NonNull T> handler) {
		Set<String> addedCoins = addHandlers(coins, handler);
		sendDataFrame(addedCoins, config.getSubscribeFrame());
	}

	public void unsubscribe(Set<String> coins) {
		Set<String> removedCoins = removeHandlers(coins);
		sendDataFrame(removedCoins, config.getUnsubscribeFrame());
	}

	protected void handleMessage(String message, PrettyWsClient client) {
		if (message == null || message.isEmpty()) return;

		try {
			JsonNode root = JSON_MAPPER.readTree(message);
			T patch = config.parser().apply(root);
			if (patch == null) return;
			Consumer<T> handler = handlersMap.get(patch.coin());
			if (handler != null) handler.accept(patch);
		} catch (JsonProcessingException ignored) {
		}
	}

	public void onConnect(Session session) {
		sendDataFrame(
						handlersMap.keySet(),
						config.getSubscribeFrame()
		);
	}
}
