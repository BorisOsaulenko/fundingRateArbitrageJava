package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.Session;
import lombok.NonNull;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class PublicWsClient {
	private static final ObjectMapper JSON_MAPPER = ObjectMapperSingleton.getInstance();
	protected final ExchangeContext context;
	protected final PublicMessageHandler messageHandler;
	protected final PublicHttpClient publicHttpClient;
	protected final CoinVector<Set<Consumer<FundingRatePatch>>> futuresFundingRateHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<BookTickerPatch>>> futuresBookTickerHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<MarkPricePatch>>> futuresMarkPriceHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<BookTickerPatch>>> spotBookTickerHandlers = new CoinVector<>();
	private final CompletableFuture<PrettyWsClient> prettyWsClientFuture; // protected for custom tweaks in subclasses

	public PublicWsClient(
					ExchangeContext context,
					URI endpoint,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttp
	) {
		this.context = context;
		this.messageHandler = messageHandler;
		this.publicHttpClient = publicHttp;
		this.prettyWsClientFuture = CompletableFuture.completedFuture(getClient(endpoint));
	}

	public PublicWsClient(
					ExchangeContext context,
					CompletableFuture<URI> endpointFuture,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttpClient
	) {
		this.context = context;
		this.messageHandler = messageHandler;
		this.publicHttpClient = publicHttpClient;
		this.prettyWsClientFuture = endpointFuture.thenApply(this::getClient);
	}

	public PublicWsClient(PublicWsClient client) {
		this.context = client.context;
		this.messageHandler = client.messageHandler;
		this.prettyWsClientFuture = client.prettyWsClientFuture;
		this.publicHttpClient = client.publicHttpClient;
	}

	public void onUnhandledDisconnect(Runnable hook) {
		this.prettyWsClientFuture.thenAccept(client -> client.onUnhandledDisconnect(hook));
	}

	private PrettyWsClient getClient(URI endpoint) {
		var client = new PrettyWsClient(
						endpoint,
						this.getClass().getSimpleName(),
						this::handleMessage
		);
		client.onOpen(this::onConnect);
		return client;
	}

	public void sendMessage(String message) {
		this.prettyWsClientFuture.thenAccept(client -> client.sendMessage(message));
	}

	public void sendObject(Object obj) {
		this.prettyWsClientFuture.thenAccept(client -> client.sendObject(obj));
	}

	public void close() {
		this.prettyWsClientFuture.thenAccept(PrettyWsClient::close);
	}

	protected abstract String getSubscribeFuturesFundingRateFrame(Set<String> symbols);

	protected abstract String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols);

	protected abstract String getSubscribeFuturesBookTickerFrame(Set<String> symbols);

	protected abstract String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols);

	protected abstract String getSubscribeFuturesMarkPriceFrame(Set<String> symbols);

	protected abstract String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols);

	protected abstract String getSubscribeSpotBookTickerFrame(Set<String> symbols);

	protected abstract String getUnsubscribeSpotBookTickerFrame(Set<String> symbols);

	protected <T> void subscribe(
					Set<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					Function<Set<String>, String> subscribeMessage
	) {
		Set<String> newSymbols = new HashSet<>();
		for (String coin : coins) {
			String symbol = context.getFuturesSymbol(coin);
			handlersMap.computeIfAbsent(
							coin, key -> {
								newSymbols.add(symbol);
								return ConcurrentHashMap.newKeySet();
							}
			).add(handler);
		}
		if (!newSymbols.isEmpty()) this.sendMessage(subscribeMessage.apply(newSymbols));
	}

	protected <T> void unsubscribe(
					Set<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Function<Set<String>, String> unsubscribeMessage
	) {
		Set<String> removedSymbols = new HashSet<>();
		for (String coin : coins) {
			String symbol = context.getFuturesSymbol(coin);
			if (handlersMap.containsKey(coin)) {
				handlersMap.remove(coin);
				removedSymbols.add(symbol);
			}
		}
		if (!removedSymbols.isEmpty()) this.sendMessage(unsubscribeMessage.apply(removedSymbols));
	}

	private <T> void removeHandler(
					Set<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					Function<Set<String>, String> unsubscribeAction
	) {
		Set<String> removedSymbols = new HashSet<>();
		for (String coin : coins) {
			Set<Consumer<T>> handlers = handlersMap.get(coin);
			if (handlers == null) continue;

			handlers.remove(handler);
			if (handlers.isEmpty()) {
				handlersMap.remove(coin, handlers);
				removedSymbols.add(context.getFuturesSymbol(coin));
			}
		}

		if (!removedSymbols.isEmpty()) this.sendMessage(unsubscribeAction.apply(removedSymbols));
	}

	public void subscribeFuturesFundingRates(Set<String> coins, Consumer<@NonNull FundingRatePatch> handler) {
		subscribe(coins, futuresFundingRateHandlers, handler, this::getSubscribeFuturesFundingRateFrame);
	}

	public void subscribeFuturesFundingRates(String coin, Consumer<@NonNull FundingRatePatch> handler) {
		subscribeFuturesFundingRates(Set.of(coin), handler);
	}

	public void unsubscribeFuturesFundingRates(Set<String> coins) {
		unsubscribe(coins, futuresFundingRateHandlers, this::getUnsubscribeFuturesFundingRateFrame);
	}

	public void unsubscribeFuturesFundingRates(String coin) {
		unsubscribeFuturesFundingRates(Set.of(coin));
	}

	public void subscribeFuturesBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		subscribe(coins, futuresBookTickerHandlers, handler, this::getSubscribeFuturesBookTickerFrame);
	}

	public void subscribeFuturesBookTicker(String coin, Consumer<@NonNull BookTickerPatch> handler) {
		subscribeFuturesBookTicker(Set.of(coin), handler);
	}

	public void unsubscribeFuturesBookTicker(Set<String> coins) {
		unsubscribe(coins, futuresBookTickerHandlers, this::getUnsubscribeFuturesBookTickerFrame);
	}

	public void unsubscribeFuturesBookTicker(String coin) {
		unsubscribeFuturesBookTicker(Set.of(coin));
	}

	public void subscribeFuturesMarkPrice(Set<String> coins, Consumer<@NonNull MarkPricePatch> handler) {
		subscribe(coins, futuresMarkPriceHandlers, handler, this::getSubscribeFuturesMarkPriceFrame);
	}

	public void subscribeFuturesMarkPrice(String coin, Consumer<@NonNull MarkPricePatch> handler) {
		subscribeFuturesMarkPrice(Set.of(coin), handler);
	}

	public void unsubscribeFuturesMarkPrice(Set<String> coins) {
		unsubscribe(coins, futuresMarkPriceHandlers, this::getUnsubscribeFuturesMarkPriceFrame);
	}

	public void unsubscribeFuturesMarkPrice(String coin) {
		unsubscribeFuturesMarkPrice(Set.of(coin));
	}

	public void subscribeSpotBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		subscribe(coins, spotBookTickerHandlers, handler, this::getSubscribeSpotBookTickerFrame);
	}

	public void subscribeSpotBookTicker(String coin, Consumer<@NonNull BookTickerPatch> handler) {
		subscribeSpotBookTicker(Set.of(coin), handler);
	}

	public void unsubscribeSpotBookTicker(Set<String> coins) {
		unsubscribe(coins, spotBookTickerHandlers, this::getUnsubscribeSpotBookTickerFrame);
	}

	protected <R extends GenericPublicWsPatch> void dispatchPatchToHandlers(
					R patch,
					CoinVector<Set<Consumer<R>>> handlersMap
	) {
		if (patch == null) return;

		Set<Consumer<R>> handlers = handlersMap.get(patch.coin());
		if (handlers != null) {
			for (Consumer<R> handler : handlers) {
				handler.accept(patch);
			}
		}
	}

	protected void handleFuturesBookTickerPatch(@NonNull BookTickerPatch patch) {
		dispatchPatchToHandlers(patch, futuresBookTickerHandlers);
	}

	protected void handleFuturesMarkPricePatch(@NonNull MarkPricePatch patch) {
		dispatchPatchToHandlers(patch, futuresMarkPriceHandlers);
	}

	protected void handleFuturesFundingRatePatch(@NonNull FundingRatePatch patch) {
		dispatchPatchToHandlers(patch, futuresFundingRateHandlers);
	}

	protected void handleSpotBookTickerPatch(@NonNull BookTickerPatch patch) {
		dispatchPatchToHandlers(patch, spotBookTickerHandlers);
	}

	protected boolean handlePingMessage(String message) {
		String pingResponse = messageHandler.getResponseToPingMessage(message);
		if (pingResponse != null) {
			this.prettyWsClientFuture.thenAccept(client -> client.sendMessage(pingResponse));
			return true;
		}
		return false;
	}

	private <T extends GenericPublicWsPatch> boolean tryHandle(
					JsonNode root,
					Function<JsonNode, T> parser,
					Consumer<T> handler
	) {
		T patch = parser.apply(root);
		if (patch == null) return false;
		handler.accept(patch);
		return true;
	}

	private void handleMessage(String message) {
		if (message == null || message.isEmpty()) return;

		JsonNode root = null;
		boolean s = false;
		try {
			root = JSON_MAPPER.readTree(message);
			if (tryHandle(root, messageHandler::parseBookTickerMessageSymbol, this::handleFuturesBookTickerPatch)) s = true;
			if (tryHandle(root, messageHandler::parseMarkPriceMessageSymbol, this::handleFuturesMarkPricePatch)) s = true;
			if (tryHandle(root, messageHandler::parseFundingRateMessageSymbol, this::handleFuturesFundingRatePatch)) s = true;
			if (tryHandle(root, messageHandler::parseSpotBookTickerMessageSymbol, this::handleSpotBookTickerPatch)) s = true;
		} catch (JsonProcessingException ignored) {
		}

		if (handlePingMessage(message)) s = true;
		//		if (!s) Logger.warn("Unrecognized public ws message: " + message);
	}

	public void onConnect(Session session) {
		Set<String> frSymbols = futuresFundingRateHandlers.keySet()
						.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!frSymbols.isEmpty()) this.sendMessage(getSubscribeFuturesFundingRateFrame(frSymbols));

		Set<String> btSymbols = futuresBookTickerHandlers.keySet()
						.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!btSymbols.isEmpty()) this.sendMessage(getSubscribeFuturesBookTickerFrame(btSymbols));

		Set<String> mpSymbols = futuresMarkPriceHandlers.keySet()
						.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!mpSymbols.isEmpty()) this.sendMessage(getSubscribeFuturesMarkPriceFrame(mpSymbols));
	}

	public boolean connected() {
		return this.prettyWsClientFuture.isDone() && this.prettyWsClientFuture.join().isConnected();
	}

	public CompletableFuture<Void> connect() {
		return this.prettyWsClientFuture.thenAccept(PrettyWsClient::connect);
	}

	public void unsubscribeCoin(String coin) {
		unsubscribeFuturesBookTicker(coin);
		unsubscribeFuturesFundingRates(coin);
		unsubscribeFuturesMarkPrice(coin);
	}

	public void unsubscribeCoins(Set<String> coins) {
		unsubscribeFuturesBookTicker(coins);

	}
}
