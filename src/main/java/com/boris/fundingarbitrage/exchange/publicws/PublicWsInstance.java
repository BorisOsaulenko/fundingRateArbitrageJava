package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.Session;
import lombok.NonNull;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PublicWsInstance {
	private static final ObjectMapper JSON_MAPPER = ObjectMapperSingleton.getInstance();
	private final ExchangeContext context;
	private final MessageHandler messageHandler;
	private final TradeMarket market;
	private final CoinVector<Set<Consumer<FundingRatePatch>>> futuresFundingRateHandlers = new CoinVector<>();
	private final CoinVector<Set<Consumer<BookTickerPatch>>> futuresBookTickerHandlers = new CoinVector<>();
	private final CoinVector<Set<Consumer<MarkPricePatch>>> futuresMarkPriceHandlers = new CoinVector<>();
	private final CoinVector<Set<Consumer<BookTickerPatch>>> spotBookTickerHandlers = new CoinVector<>();
	private final PublicWsFrames wsFrames;
	private final PrettyWsClient client; // protected for custom tweaks in subclasses
	private final int chunkSize;

	public PublicWsInstance(
					ExchangeContext context,
					URI endpoint,
					MessageHandler messageHandler,
					PublicWsFrames wsFrames,
					int chunkSize,
					TradeMarket market
	) {
		this.context = context;
		this.messageHandler = messageHandler;
		this.wsFrames = wsFrames;
		this.client = this.getClient(endpoint);
		this.chunkSize = chunkSize;
		this.market = market;
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

	public void connect() {
		client.connect();
	}

	public void close() {
		client.close();
	}

	public void sendPing() {
		String pingFrame = switch (market) {
			case SPOT -> messageHandler.getResponseToSpotPingMessage(null);
			case FUTURES -> messageHandler.getResponseToFuturesPingMessage(null);
		};
		if (pingFrame != null) client.sendMessage(pingFrame);
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

	protected <T> Set<String> addHandlers(
					Set<String> coins,
					CoinVector<Set<Consumer<T>>> handlersMap,
					Consumer<T> handler
	) {
		Set<String> addedCoins = new HashSet<>();
		for (String coin : coins) {
			handlersMap.computeIfAbsent(
							coin, c -> {
								addedCoins.add(c);
								return ConcurrentHashMap.newKeySet();
							}
			).add(handler);
		}
		return addedCoins;
	}

	protected <T> Set<String> removeHandlers(
					Set<String> coins,
					Map<String, Set<Consumer<T>>> handlersMap
	) {
		Set<String> removedCoins = new HashSet<>();
		for (String coin : coins) {
			if (handlersMap.remove(coin) != null) removedCoins.add(coin);
		}
		return removedCoins;
	}

	protected void sendDataFrame(
					Set<String> coins,
					Function<String, String> symbolGetter,
					Function<Set<String>, String> subscribeMessage
	) {
		if (coins.isEmpty()) return;
		Set<String> symbols = coins.stream().map(symbolGetter).collect(Collectors.toSet());

		sendChunks(symbols, chunkSize, subscribeMessage);
	}

	public void subscribeFuturesFundingRates(Set<String> coins, Consumer<@NonNull FundingRatePatch> handler) {
		Set<String> addedCoins = addHandlers(coins, futuresFundingRateHandlers, handler);
		sendDataFrame(addedCoins, context::getFuturesSymbol, wsFrames::getSubscribeFuturesFundingRateFrame);
	}

	public void unsubscribeFuturesFundingRates(Set<String> coins) {
		Set<String> removedCoins = removeHandlers(coins, futuresFundingRateHandlers);
		sendDataFrame(removedCoins, context::getFuturesSymbol, wsFrames::getUnsubscribeFuturesFundingRateFrame);
	}

	public void subscribeFuturesBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		Set<String> addedCoins = addHandlers(coins, futuresBookTickerHandlers, handler);
		sendDataFrame(addedCoins, context::getFuturesSymbol, wsFrames::getSubscribeFuturesBookTickerFrame);
	}

	public void unsubscribeFuturesBookTicker(Set<String> coins) {
		Set<String> removedCoins = removeHandlers(coins, futuresBookTickerHandlers);
		sendDataFrame(removedCoins, context::getFuturesSymbol, wsFrames::getUnsubscribeFuturesBookTickerFrame);
	}

	public void subscribeFuturesMarkPrice(Set<String> coins, Consumer<@NonNull MarkPricePatch> handler) {
		Set<String> addedCoins = addHandlers(coins, futuresMarkPriceHandlers, handler);
		sendDataFrame(addedCoins, context::getFuturesSymbol, wsFrames::getSubscribeFuturesMarkPriceFrame);
	}

	public void unsubscribeFuturesMarkPrice(Set<String> coins) {
		Set<String> removedCoins = removeHandlers(coins, futuresMarkPriceHandlers);
		sendDataFrame(removedCoins, context::getFuturesSymbol, wsFrames::getUnsubscribeFuturesMarkPriceFrame);
	}

	public void subscribeSpotBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		Set<String> addedCoins = addHandlers(coins, spotBookTickerHandlers, handler);
		sendDataFrame(addedCoins, context::getSpotSymbol, wsFrames::getSubscribeSpotBookTickerFrame);
	}

	public void unsubscribeSpotBookTicker(Set<String> coins) {
		Set<String> removedCoins = removeHandlers(coins, spotBookTickerHandlers);
		sendDataFrame(removedCoins, context::getSpotSymbol, wsFrames::getUnsubscribeSpotBookTickerFrame);
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

	private void handleSpotBookTickerPatch(@NonNull BookTickerPatch patch) {
		dispatchPatchToHandlers(patch, spotBookTickerHandlers);
	}

	protected void handlePingMessage(String message) {
		String pingResponse = switch (market) {
			case SPOT -> messageHandler.getResponseToSpotPingMessage(message);
			case FUTURES -> messageHandler.getResponseToFuturesPingMessage(message);
		};
		if (pingResponse != null) client.sendMessage(pingResponse);
	}

	private <T extends GenericPublicWsPatch> void tryHandle(
					JsonNode root,
					Function<JsonNode, T> parser,
					Consumer<T> handler
	) {
		try {
			T patch = parser.apply(root);
			if (patch != null) handler.accept(patch);
		} catch (Exception _) {
		}
	}

	protected void handleFuturesMessage(JsonNode root) {
		try {
			tryHandle(root, messageHandler::parseFuturesBookTickerMessageSymbol, this::handleFuturesBookTickerPatch);
			tryHandle(root, messageHandler::parseMarkPriceMessageSymbol, this::handleFuturesMarkPricePatch);
			tryHandle(root, messageHandler::parseFundingRateMessageSymbol, this::handleFuturesFundingRatePatch);
		} catch (Exception _) {
		}
	}

	protected void handleSpotMessage(JsonNode root) {
		try {
			tryHandle(root, messageHandler::parseSpotBookTickerMessageSymbol, this::handleSpotBookTickerPatch);
		} catch (Exception _) {
		}
	}

	protected void handleMessage(String message, PrettyWsClient client) {
		if (message == null || message.isEmpty()) return;

		try {
			JsonNode root = JSON_MAPPER.readTree(message);
			switch (market) {
				case SPOT -> handleSpotMessage(root);
				case FUTURES -> handleFuturesMessage(root);
			}
		} catch (JsonProcessingException ignored) {
		}

		handlePingMessage(message);
	}

	public void onConnect(Session session) {
		sendDataFrame(
						futuresFundingRateHandlers.keySet(),
						context::getFuturesSymbol,
						wsFrames::getSubscribeFuturesFundingRateFrame
		);
		sendDataFrame(
						futuresBookTickerHandlers.keySet(),
						context::getFuturesSymbol,
						wsFrames::getSubscribeFuturesBookTickerFrame
		);
		sendDataFrame(
						futuresMarkPriceHandlers.keySet(),
						context::getFuturesSymbol,
						wsFrames::getSubscribeFuturesMarkPriceFrame
		);
		sendDataFrame(spotBookTickerHandlers.keySet(), context::getSpotSymbol, wsFrames::getSubscribeSpotBookTickerFrame);
	}

	public void unsubscribeCoinsFutures(Set<String> coins) {
		unsubscribeFuturesBookTicker(coins);
		unsubscribeFuturesMarkPrice(coins);
		unsubscribeFuturesFundingRates(coins);
	}

	public void unsubscribeCoinsSpot(Set<String> coins) {
		unsubscribeSpotBookTicker(coins);
	}
}
