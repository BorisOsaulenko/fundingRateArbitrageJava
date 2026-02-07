package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClient;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClientBuilder;
import lombok.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class PublicWsClient {
	protected final PrettyWsClient prettyWsClient; // protected for custom tweaks in subclasses
	protected final ExchangeContext exchangeContext;
	protected final PublicMessageHandler messageHandler;
	protected final PublicHttpClient publicHttpClient;
	protected final CoinVector<Set<Consumer<FundingRatePatch>>> fundingRateHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<BookTickerPatch>>> bookTickerHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<MarkPricePatch>>> markPriceHandlers = new CoinVector<>();

	protected PublicWsClient(
					ExchangeContext context,
					URI endpoint,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttp
	) {
		this.exchangeContext = context;
		this.messageHandler = messageHandler;
		this.publicHttpClient = publicHttp;
		this.prettyWsClient = new PrettyWsClientBuilder(endpoint, this::handleMessage).build();
	}

	protected PublicWsClient(PublicWsClient client) {
		this.exchangeContext = client.exchangeContext;
		this.messageHandler = client.messageHandler;
		this.prettyWsClient = client.prettyWsClient;
		this.publicHttpClient = client.publicHttpClient;
	}

	public void close() {
		this.prettyWsClient.close();
	}

	protected abstract void sendSubscribeFundingRateFrame(String[] symbols);

	protected abstract void sendUnsubscribeFundingRateFrame(String[] symbols);

	protected abstract void sendSubscribeBookTickerFrame(String[] symbols);

	protected abstract void sendUnsubscribeBookTickerFrame(String[] symbols);

	protected abstract void sendSubscribeMarkPriceFrame(String[] symbols);

	protected abstract void sendUnsubscribeMarkPriceFrame(String[] symbols);

	private <T> void subscribe(
					String[] coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					Consumer<String[]> subscribeAction
	) {
		List<String> newSymbols = new ArrayList<>();
		for (String coin : coins) {
			String symbol = exchangeContext.getSymbol(coin);
			handlersMap.computeIfAbsent(
							coin, key -> {
								newSymbols.add(symbol);
								return ConcurrentHashMap.newKeySet();
							}
			).add(handler);
		}
		if (!newSymbols.isEmpty()) subscribeAction.accept(newSymbols.toArray(new String[0]));
	}

	private <T> void unsubscribe(
					String[] coins,
					Map<String, Set<Consumer<T>>> handlersMap,
					Consumer<String[]> unsubscribeAction
	) {
		List<String> removedSymbols = new ArrayList<>();
		for (String coin : coins) {
			String symbol = exchangeContext.getSymbol(coin);
			if (handlersMap.containsKey(symbol)) {
				handlersMap.remove(coin);
				removedSymbols.add(symbol);
			}
		}
		if (!removedSymbols.isEmpty()) unsubscribeAction.accept(removedSymbols.toArray(new String[0]));
	}

	public final void subscribeFundingRates(String[] coins, Consumer<@NonNull FundingRatePatch> handler) {
		subscribe(coins, fundingRateHandlers, handler, this::sendSubscribeFundingRateFrame);
	}

	public final void subscribeFundingRates(String coin, Consumer<@NonNull FundingRatePatch> handler) {
		subscribeFundingRates(new String[]{coin}, handler);
	}

	public final void unsubscribeFundingRates(String[] coins) {
		unsubscribe(coins, fundingRateHandlers, this::sendUnsubscribeFundingRateFrame);
	}

	public final void unsubscribeFundingRates(String coin) {
		unsubscribeFundingRates(new String[]{coin});
	}

	public final void subscribeBookTicker(String[] coins, Consumer<@NonNull BookTickerPatch> handler) {
		subscribe(coins, bookTickerHandlers, handler, this::sendSubscribeBookTickerFrame);
	}

	public final void subscribeBookTicker(String coin, Consumer<@NonNull BookTickerPatch> handler) {
		subscribeBookTicker(new String[]{coin}, handler);
	}

	public final void unsubscribeBookTicker(String[] coins) {
		unsubscribe(coins, bookTickerHandlers, this::sendUnsubscribeBookTickerFrame);
	}

	public final void unsubscribeBookTicker(String coin) {
		unsubscribeBookTicker(new String[]{coin});
	}

	public final void subscribeMarkPrice(String[] coins, Consumer<@NonNull MarkPricePatch> handler) {
		subscribe(coins, markPriceHandlers, handler, this::sendSubscribeMarkPriceFrame);
	}

	public final void subscribeMarkPrice(String coin, Consumer<@NonNull MarkPricePatch> handler) {
		subscribeMarkPrice(new String[]{coin}, handler);
	}

	public final void unsubscribeMarkPrice(String[] coins) {
		unsubscribe(coins, markPriceHandlers, this::sendUnsubscribeMarkPriceFrame);
	}

	public final void unsubscribeMarkPrice(String coin) {
		unsubscribeMarkPrice(new String[]{coin});
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

	protected void handlePingMessage(String message) {
		String pingResponse = messageHandler.getResponseToPingMessage(message);
		if (pingResponse != null) {
			this.prettyWsClient.sendMessage(pingResponse);
		}
	}

	private <T extends GenericPublicWsPatch> boolean tryHandle(
					String message,
					Function<String, T> parser,
					Consumer<T> handler
	) {
		T patch = parser.apply(message);
		if (patch == null) return false;
		handler.accept(patch);
		return true;
	}

	private void handleMessage(String message) {
		if (message == null || message.isEmpty()) return;

		if (tryHandle(message, messageHandler::parseBookTickerMessageSymbol, this::handleBookTickerPatch)) return;
		if (tryHandle(message, messageHandler::parseMarkPriceMessageSymbol, this::handleMarkPricePatch)) return;
		if (tryHandle(message, messageHandler::parseFundingRateMessageSymbol, this::handleFundingRatePatch)) return;

		handlePingMessage(message);
	}


	public void unsubscribeSymbol(String symbol) {
		unsubscribeBookTicker(symbol);
		unsubscribeFundingRates(symbol);
		unsubscribeMarkPrice(symbol);
	}
}
