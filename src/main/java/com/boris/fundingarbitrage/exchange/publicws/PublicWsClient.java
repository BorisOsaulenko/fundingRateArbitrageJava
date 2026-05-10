package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.scheduler.ModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.ModifiableSchedulerBuilder;
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

public abstract class PublicWsClient implements PublicMarketDataStream {
	private static final ObjectMapper JSON_MAPPER = ObjectMapperSingleton.getInstance();
	protected final ExchangeContext context;
	protected final PublicMessageHandler messageHandler;
	protected final PublicHttpClient publicHttpClient;
	protected final CoinVector<Set<Consumer<FundingRatePatch>>> futuresFundingRateHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<BookTickerPatch>>> futuresBookTickerHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<MarkPricePatch>>> futuresMarkPriceHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<BookTickerPatch>>> spotBookTickerHandlers = new CoinVector<>();
	protected final ModifiableScheduler pingScheduler;
	private final CompletableFuture<PrettyWsClient> prettyWsClientFuture; // protected for custom tweaks in subclasses
	private final long pingFrequency = 20 * 1000L;

	public PublicWsClient(
					ExchangeContext context,
					URI endpoint,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttp,
					ModifiableSchedulerBuilder schedulerBuilder
	) {
		this.context = context;
		this.messageHandler = messageHandler;
		this.publicHttpClient = publicHttp;
		this.prettyWsClientFuture = CompletableFuture.completedFuture(getClient(endpoint));
		this.pingScheduler = schedulerBuilder.create(() -> sendMessage(getPingFrame()), pingFrequency);
	}

	public PublicWsClient(
					ExchangeContext context,
					CompletableFuture<URI> endpointFuture,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttpClient,
					ModifiableSchedulerBuilder schedulerBuilder
	) {
		this.context = context;
		this.messageHandler = messageHandler;
		this.publicHttpClient = publicHttpClient;
		this.prettyWsClientFuture = endpointFuture.thenApply(this::getClient);
		this.pingScheduler = schedulerBuilder.create(() -> sendMessage(getPingFrame()), pingFrequency);
	}

	public void onUnhandledDisconnect(Runnable hook) {
		this.prettyWsClientFuture.thenAccept(client -> {
			if (client != null) client.onUnhandledDisconnect(hook);
		});
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
		this.prettyWsClientFuture.thenAccept(client -> {
			if (client != null) client.sendMessage(message);
		});
	}

	public void sendObject(Object obj) {
		this.prettyWsClientFuture.thenAccept(client -> {
			if (client != null) client.sendObject(obj);
		});
	}

	public void close() {
		this.prettyWsClientFuture.thenAccept(client -> {
			if (client != null) client.close();
		});
	}

	protected abstract String getPingFrame();

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
					Function<String, String> symbolGetter,
					Function<Set<String>, String> subscribeMessage,
					Consumer<String> messageSender
	) {
		Set<String> newSymbols = new HashSet<>();
		for (String coin : coins) {
			String symbol = symbolGetter.apply(coin);
			handlersMap.computeIfAbsent(
							coin, key -> {
								newSymbols.add(symbol);
								return ConcurrentHashMap.newKeySet();
							}
			).add(handler);
		}
		if (!newSymbols.isEmpty()) messageSender.accept(subscribeMessage.apply(newSymbols));
	}

	protected <T> void unsubscribe(
					Set<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Function<String, String> symbolGetter,
					Function<Set<String>, String> unsubscribeMessage,
					Consumer<String> messageSender
	) {
		Set<String> removedSymbols = new HashSet<>();
		for (String coin : coins) {
			String symbol = symbolGetter.apply(coin);
			if (handlersMap.containsKey(coin)) {
				handlersMap.remove(coin);
				removedSymbols.add(symbol);
			}
		}
		if (!removedSymbols.isEmpty()) messageSender.accept(unsubscribeMessage.apply(removedSymbols));
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
		subscribe(
						coins,
						futuresFundingRateHandlers,
						handler,
						context::getFuturesSymbol,
						this::getSubscribeFuturesFundingRateFrame,
						this::sendMessage
		);
	}

	public void unsubscribeFuturesFundingRates(Set<String> coins) {
		unsubscribe(
						coins,
						futuresFundingRateHandlers,
						context::getFuturesSymbol,
						this::getUnsubscribeFuturesFundingRateFrame,
						this::sendMessage
		);
	}

	public void subscribeFuturesBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		subscribe(
						coins,
						futuresBookTickerHandlers,
						handler,
						context::getFuturesSymbol,
						this::getSubscribeFuturesBookTickerFrame,
						this::sendMessage
		);
	}

	public void unsubscribeFuturesBookTicker(Set<String> coins) {
		unsubscribe(
						coins,
						futuresBookTickerHandlers,
						context::getFuturesSymbol,
						this::getUnsubscribeFuturesBookTickerFrame,
						this::sendMessage
		);
	}

	public void subscribeFuturesMarkPrice(Set<String> coins, Consumer<@NonNull MarkPricePatch> handler) {
		subscribe(
						coins,
						futuresMarkPriceHandlers,
						handler,
						context::getFuturesSymbol,
						this::getSubscribeFuturesMarkPriceFrame,
						this::sendMessage
		);
	}

	public void unsubscribeFuturesMarkPrice(Set<String> coins) {
		unsubscribe(
						coins,
						futuresMarkPriceHandlers,
						context::getFuturesSymbol,
						this::getUnsubscribeFuturesMarkPriceFrame,
						this::sendMessage
		);
	}

	public void subscribeSpotBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		subscribe(
						coins,
						spotBookTickerHandlers,
						handler,
						context::getSpotSymbol,
						this::getSubscribeSpotBookTickerFrame,
						this::sendMessage
		);
	}

	public void unsubscribeSpotBookTicker(Set<String> coins) {
		unsubscribe(
						coins,
						spotBookTickerHandlers,
						context::getSpotSymbol,
						this::getUnsubscribeSpotBookTickerFrame,
						this::sendMessage
		);
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
			this.prettyWsClientFuture.thenAccept(client -> {
				if (client != null) client.sendMessage(pingResponse);
			});
			return true;
		}
		return false;
	}

	private <T extends GenericPublicWsPatch> boolean tryHandle(
					JsonNode root,
					Function<JsonNode, T> parser,
					Consumer<T> handler
	) {
		try {
			T patch = parser.apply(root);
			if (patch != null) handler.accept(patch);
		} catch (Exception ex) {
			return false;
		}

		return true;
	}

	protected void handleMessage(String message) {
		if (message == null || message.isEmpty()) return;

		JsonNode root = null;
		boolean s = false;
		try {
			root = JSON_MAPPER.readTree(message);
			if (tryHandle(root, messageHandler::parseFuturesBookTickerMessageSymbol, this::handleFuturesBookTickerPatch))
				s = true;
			if (tryHandle(root, messageHandler::parseMarkPriceMessageSymbol, this::handleFuturesMarkPricePatch)) s = true;
			if (tryHandle(root, messageHandler::parseFundingRateMessageSymbol, this::handleFuturesFundingRatePatch)) s = true;
			if (tryHandle(root, messageHandler::parseSpotBookTickerMessageSymbol, this::handleSpotBookTickerPatch)) s = true;
		} catch (JsonProcessingException ignored) {
		}

		if (handlePingMessage(message)) s = true;
		//		if (!s) Logger.warn("Unrecognized public ws message: " + message);
	}

	public void onConnect(Session session) {
		Set<String> futuresFundingRateSymbols = futuresFundingRateHandlers.keySet()
						.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!futuresFundingRateSymbols.isEmpty())
			this.sendMessage(getSubscribeFuturesFundingRateFrame(futuresFundingRateSymbols));

		Set<String> futuresBookTickerSymbols = futuresBookTickerHandlers.keySet()
						.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!futuresBookTickerSymbols.isEmpty())
			this.sendMessage(getSubscribeFuturesBookTickerFrame(futuresBookTickerSymbols));

		Set<String> futuresMarkPriceSymbols = futuresMarkPriceHandlers.keySet()
						.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!futuresMarkPriceSymbols.isEmpty())
			this.sendMessage(getSubscribeFuturesMarkPriceFrame(futuresMarkPriceSymbols));

		Set<String> spotBookTickerSymbols = spotBookTickerHandlers.keySet()
						.stream()
						.map(context::getSpotSymbol)
						.collect(Collectors.toSet());
		if (!spotBookTickerSymbols.isEmpty())
			this.sendMessage(getSubscribeSpotBookTickerFrame(spotBookTickerSymbols));
	}

	public boolean connected() {
		return this.prettyWsClientFuture.isDone()
					 && this.prettyWsClientFuture.join() != null
					 && this.prettyWsClientFuture.join().isConnected();
	}

	public CompletableFuture<Void> connect() {
		return this.prettyWsClientFuture.thenAccept(client -> {
			if (client != null) client.connect();
		});
	}

	public void unsubscribeCoinsFutures(Set<String> coins) {
		unsubscribeFuturesBookTicker(coins);
		unsubscribeFuturesFundingRates(coins);
		unsubscribeFuturesMarkPrice(coins);
	}

	public void unsubscribeCoinsSpot(Set<String> coins) {
		unsubscribeSpotBookTicker(coins);
	}
}
