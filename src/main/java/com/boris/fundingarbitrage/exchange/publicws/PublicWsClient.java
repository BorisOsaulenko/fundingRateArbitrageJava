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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class PublicWsClient {
	private static final ObjectMapper JSON_MAPPER = ObjectMapperSingleton.getInstance();
	protected final ExchangeContext context;
	protected final PublicMessageHandler messageHandler;
	protected final PublicHttpClient publicHttpClient;
	protected final CoinVector<Set<Consumer<FundingRatePatch>>> fundingRateHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<BookTickerPatch>>> bookTickerHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<MarkPricePatch>>> markPriceHandlers = new CoinVector<>();
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
		this.prettyWsClientFuture = CompletableFuture.completedFuture(new PrettyWsClient(
						endpoint,
						this.getClass().getSimpleName(),
						this::handleMessage,
						this::onConnect,
						null
		));
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
		this.prettyWsClientFuture = endpointFuture.thenApply(endpoint -> new PrettyWsClient(
						endpoint,
						this.getClass().getSimpleName(),
						this::handleMessage,
						this::onConnect,
						null
		));
	}

	public PublicWsClient(PublicWsClient client) {
		this.context = client.context;
		this.messageHandler = client.messageHandler;
		this.prettyWsClientFuture = client.prettyWsClientFuture;
		this.publicHttpClient = client.publicHttpClient;
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

	protected abstract String getSubscribeFundingRateFrame(List<String> symbols);

	protected abstract String getUnsubscribeFundingRateFrame(List<String> symbols);

	protected abstract String getSubscribeBookTickerFrame(List<String> symbols);

	protected abstract String getUnsubscribeBookTickerFrame(List<String> symbols);

	protected abstract String getSubscribeMarkPriceFrame(List<String> symbols);

	protected abstract String getUnsubscribeMarkPriceFrame(List<String> symbols);

	private <T> void subscribe(
					List<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					Function<List<String>, String> subscribeMessage
	) {
		List<String> newSymbols = new ArrayList<>();
		for (String coin : coins) {
			String symbol = context.getSymbol(coin);
			handlersMap.computeIfAbsent(
							coin, key -> {
								newSymbols.add(symbol);
								return ConcurrentHashMap.newKeySet();
							}
			).add(handler);
		}
		if (!newSymbols.isEmpty()) this.sendMessage(subscribeMessage.apply(newSymbols));
	}

	private <T> void unsubscribe(
					List<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Function<List<String>, String> unsubscribeAction
	) {
		List<String> removedSymbols = new ArrayList<>();
		for (String coin : coins) {
			String symbol = context.getSymbol(coin);
			if (handlersMap.containsKey(coin)) {
				handlersMap.remove(coin);
				removedSymbols.add(symbol);
			}
		}
		if (!removedSymbols.isEmpty()) this.sendMessage(unsubscribeAction.apply(removedSymbols));
	}

	private <T> void removeHandler(
					List<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					Function<List<String>, String> unsubscribeAction
	) {
		List<String> removedSymbols = new ArrayList<>();
		for (String coin : coins) {
			Set<Consumer<T>> handlers = handlersMap.get(coin);
			if (handlers == null) continue;

			handlers.remove(handler);
			if (handlers.isEmpty()) {
				handlersMap.remove(coin, handlers);
				removedSymbols.add(context.getSymbol(coin));
			}
		}

		if (!removedSymbols.isEmpty()) this.sendMessage(unsubscribeAction.apply(removedSymbols));
	}

	public void subscribeFundingRates(List<String> coins, Consumer<@NonNull FundingRatePatch> handler) {
		subscribe(coins, fundingRateHandlers, handler, this::getSubscribeFundingRateFrame);
	}

	public void subscribeFundingRates(String coin, Consumer<@NonNull FundingRatePatch> handler) {
		subscribeFundingRates(List.of(coin), handler);
	}

	public void unsubscribeFundingRates(List<String> coins) {
		unsubscribe(coins, fundingRateHandlers, this::getUnsubscribeFundingRateFrame);
	}

	public void unsubscribeFundingRates(String coin) {
		unsubscribeFundingRates(List.of(coin));
	}

	public void removeFundingRatesHandler(List<String> coins, Consumer<@NonNull FundingRatePatch> handler) {
		removeHandler(coins, fundingRateHandlers, handler, this::getUnsubscribeFundingRateFrame);
	}

	public void removeFundingRatesHandler(String coin, Consumer<@NonNull FundingRatePatch> handler) {
		removeFundingRatesHandler(List.of(coin), handler);
	}

	public void subscribeBookTicker(List<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		subscribe(coins, bookTickerHandlers, handler, this::getSubscribeBookTickerFrame);
	}

	public void subscribeBookTicker(String coin, Consumer<@NonNull BookTickerPatch> handler) {
		subscribeBookTicker(List.of(coin), handler);
	}

	public void unsubscribeBookTicker(List<String> coins) {
		unsubscribe(coins, bookTickerHandlers, this::getUnsubscribeBookTickerFrame);
	}

	public void unsubscribeBookTicker(String coin) {
		unsubscribeBookTicker(List.of(coin));
	}

	public void removeBookTickerHandler(List<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		removeHandler(coins, bookTickerHandlers, handler, this::getUnsubscribeBookTickerFrame);
	}

	public void removeBookTickerHandler(String coin, Consumer<@NonNull BookTickerPatch> handler) {
		removeBookTickerHandler(List.of(coin), handler);
	}

	public void subscribeMarkPrice(List<String> coins, Consumer<@NonNull MarkPricePatch> handler) {
		subscribe(coins, markPriceHandlers, handler, this::getSubscribeMarkPriceFrame);
	}

	public void subscribeMarkPrice(String coin, Consumer<@NonNull MarkPricePatch> handler) {
		subscribeMarkPrice(List.of(coin), handler);
	}

	public void unsubscribeMarkPrice(List<String> coins) {
		unsubscribe(coins, markPriceHandlers, this::getUnsubscribeMarkPriceFrame);
	}

	public void unsubscribeMarkPrice(String coin) {
		unsubscribeMarkPrice(List.of(coin));
	}

	public void removeMarkPriceHandler(List<String> coins, Consumer<@NonNull MarkPricePatch> handler) {
		removeHandler(coins, markPriceHandlers, handler, this::getUnsubscribeMarkPriceFrame);
	}

	public void removeMarkPriceHandler(String coin, Consumer<@NonNull MarkPricePatch> handler) {
		removeMarkPriceHandler(List.of(coin), handler);
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

	protected void handleBookTickerPatch(@NonNull BookTickerPatch patch) {
		dispatchPatchToHandlers(patch, bookTickerHandlers);
	}

	protected void handleMarkPricePatch(@NonNull MarkPricePatch patch) {
		dispatchPatchToHandlers(patch, markPriceHandlers);
	}

	protected void handleFundingRatePatch(@NonNull FundingRatePatch patch) {
		dispatchPatchToHandlers(patch, fundingRateHandlers);
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
			if (tryHandle(root, messageHandler::parseBookTickerMessageSymbol, this::handleBookTickerPatch)) s = true;
			if (tryHandle(root, messageHandler::parseMarkPriceMessageSymbol, this::handleMarkPricePatch)) s = true;
			if (tryHandle(root, messageHandler::parseFundingRateMessageSymbol, this::handleFundingRatePatch)) s = true;
		} catch (JsonProcessingException ignored) {}

		if (handlePingMessage(message)) s = true;
		//		if (!s) Logger.warn("Unrecognized public ws message: " + message);
	}

	public void onConnect(Session session) {
		List<String> fundingRateSymbols = fundingRateHandlers.keySet().stream().map(context::getSymbol).toList();
		if (!fundingRateSymbols.isEmpty()) this.sendMessage(getSubscribeFundingRateFrame(fundingRateSymbols));

		List<String> bookTickerSymbols = bookTickerHandlers.keySet().stream().map(context::getSymbol).toList();
		if (!bookTickerSymbols.isEmpty()) this.sendMessage(getSubscribeBookTickerFrame(bookTickerSymbols));

		List<String> markPriceSymbols = markPriceHandlers.keySet().stream().map(context::getSymbol).toList();
		if (!markPriceSymbols.isEmpty()) this.sendMessage(getSubscribeMarkPriceFrame(markPriceSymbols));
	}

	protected boolean connected() {
		return this.prettyWsClientFuture.isDone() && this.prettyWsClientFuture.join().isConnected();
	}

	public void unsubscribeCoin(String coin) {
		unsubscribeBookTicker(coin);
		unsubscribeFundingRates(coin);
		unsubscribeMarkPrice(coin);
	}
}
