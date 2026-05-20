package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.ObjectMapperSingleton;
import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BinancePublicWsClient extends PublicWsClient {
	private static final URI endpoint = URI.create("wss://fstream.binance.com/ws");
	private static final URI spotEndpoint = URI.create("wss://stream.binance.com:9443/ws");
	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
	private final ObjectMapper mapper = ObjectMapperSingleton.getInstance();

	private final PrettyWsClient fundingAndMarkClient;
	private final PrettyWsClient bookTickerClient;
	private final PrettyWsClient spotBookTickerClient;

	public BinancePublicWsClient(ExchangeContext context, PublicHttpClient publicHttp) {
		BinancePublicMessageHandler messageHandler = new BinancePublicMessageHandler(context);
		super(context, endpoint, messageHandler, publicHttp, new ProdModifiableSchedulerBuilder());
		this.fundingAndMarkClient = new PrettyWsClient(
						endpoint,
						"Binance Public Funding Mark",
						this::handleFundingAndMarkMessage
		);
		this.bookTickerClient = new PrettyWsClient(endpoint, "Binance Public Book Ticker", this::handleBookTickerMessage);
		this.spotBookTickerClient = new PrettyWsClient(
						spotEndpoint,
						"Binance Public Spot Book Ticker",
						this::handleSpotBookTickerMessage
		);
	}

	public static int getNextId() {
		return NEXT_ID.getAndIncrement();
	}

	private String getSubscribeFrame(Set<String> symbols, Function<String, String> toStreamMapper) {
		String[] streams = symbols.stream().map(toStreamMapper).toArray(String[]::new);
		WsRequest sub = new WsRequest("SUBSCRIBE", streams);
		return sub.toJson();
	}

	private String getUnsubscribeFrame(Set<String> symbols, Function<String, String> toStreamMapper) {
		String[] streams = symbols.stream().map(toStreamMapper).toArray(String[]::new);
		WsRequest unsub = new WsRequest("UNSUBSCRIBE", streams);
		return unsub.toJson();
	}

	private String getFundingRateStream(@NotNull String symbol) {
		return String.format("%s@mark@1s", symbol.toLowerCase());
	}

	private String getBookTickerStream(@NotNull String symbol) {
		return String.format("%s@bookTicker", symbol.toLowerCase());
	}

	@Override
	protected String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return this.getSubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	protected String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getFundingRateStream);
	}

	@Override
	protected String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return this.getSubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return this.getSubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	protected String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return this.getUnsubscribeFrame(symbols, this::getBookTickerStream);
	}

	@Override
	public void subscribeFuturesBookTicker(
					Set<String> coins,
					Consumer<BookTickerPatch> handler
	) {
		subscribeCommon(
						coins,
						handler,
						futuresBookTickerHandlers,
						futuresBookTickerHandlers::containsKey,
						this::getSubscribeFuturesBookTickerFrame,
						bookTickerClient::sendMessage
		);
	}

	@Override
	public void unsubscribeFuturesBookTicker(Set<String> coins) {
		unsubscribeCommon(
						coins,
						futuresBookTickerHandlers,
						futuresBookTickerHandlers::containsKey,
						this::getUnsubscribeFuturesBookTickerFrame,
						bookTickerClient::sendMessage
		);
	}

	@Override
	public void subscribeSpotBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribeCommon(
						coins,
						handler,
						spotBookTickerHandlers,
						spotBookTickerHandlers::containsKey,
						this::getSubscribeSpotBookTickerFrame,
						spotBookTickerClient::sendMessage
		);
	}

	@Override
	public void unsubscribeSpotBookTicker(Set<String> coins) {
		unsubscribeCommon(
						coins,
						spotBookTickerHandlers,
						spotBookTickerHandlers::containsKey,
						this::getUnsubscribeSpotBookTickerFrame,
						spotBookTickerClient::sendMessage
		);
	}

	private <T> void subscribeFundingAndMark(
					Set<String> coins,
					Consumer<T> handler,
					CoinVector<Set<Consumer<T>>> handlersMap
	) {
		subscribeCommon(
						coins,
						handler,
						handlersMap,
						coin -> futuresFundingRateHandlers.containsKey(coin) || futuresMarkPriceHandlers.containsKey(coin),
						this::getSubscribeFuturesFundingRateFrame,
						fundingAndMarkClient::sendMessage
		);
	}

	private <T> void unsubscribeFundingAndMark(
					Set<String> coins,
					CoinVector<Set<Consumer<T>>> handlersMap
	) {
		unsubscribeCommon(
						coins,
						handlersMap,
						coin -> futuresFundingRateHandlers.containsKey(coin) || futuresMarkPriceHandlers.containsKey(coin),
						this::getUnsubscribeFuturesFundingRateFrame,
						fundingAndMarkClient::sendMessage
		);
	}

	@Override
	public void subscribeFuturesFundingRates(Set<String> coinsToSub, Consumer<FundingRatePatch> handler) {
		subscribeFundingAndMark(coinsToSub, handler, futuresFundingRateHandlers);
	}

	@Override
	public void unsubscribeFuturesFundingRates(Set<String> coins) {
		unsubscribeFundingAndMark(coins, futuresFundingRateHandlers);
	}

	@Override
	public void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPricePatch> handler) {
		subscribeFundingAndMark(coins, handler, futuresMarkPriceHandlers);
	}

	@Override
	public void unsubscribeFuturesMarkPrice(Set<String> coins) {
		unsubscribeFundingAndMark(coins, futuresMarkPriceHandlers);
	}

	private void sendInBatches(
					Set<String> symbols,
					Function<Set<String>, String> frameTransformer,
					Consumer<String> sender
	) {
		int batchSize = 150;
		for (int i = 0; i < symbols.size(); i += batchSize) {
			Set<String> batch = symbols.stream().skip(i).limit(batchSize).collect(Collectors.toSet());
			if (batch.isEmpty()) continue;
			String frame = frameTransformer.apply(batch);
			sender.accept(frame);
		}
	}

	private <T> void subscribeCommon(
					Set<String> coins,
					Consumer<T> handler,
					CoinVector<Set<Consumer<T>>> handlersMap,
					Predicate<String> isSubscribed,
					Function<Set<String>, String> subscribeFrame,
					Consumer<String> sender
	) {
		Set<String> coinsToSubscribe = new HashSet<>();
		for (String coin : coins) {
			boolean wasSubscribed = isSubscribed.test(coin);
			handlersMap.computeIfAbsent(coin, v -> new HashSet<>()).add(handler);
			if (!wasSubscribed) coinsToSubscribe.add(coin);
		}

		Set<String> symbolsToSubscribe = coinsToSubscribe.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!coinsToSubscribe.isEmpty()) sendInBatches(symbolsToSubscribe, subscribeFrame, sender);
	}

	private <T> void unsubscribeCommon(
					Set<String> coins,
					CoinVector<Set<Consumer<T>>> handlersMap,
					Predicate<String> isSubscribed,
					Function<Set<String>, String> unsubscribeFrame,
					Consumer<String> sender
	) {
		Set<String> coinsToUnsubscribe = new HashSet<>();
		for (String coin : coins) {
			boolean wasSubscribed = isSubscribed.test(coin);
			if (!wasSubscribed) continue;

			handlersMap.remove(coin);
			if (!isSubscribed.test(coin)) coinsToUnsubscribe.add(coin);
		}

		Set<String> symbolsToUnsubscribe = coinsToUnsubscribe.stream()
						.map(context::getFuturesSymbol)
						.collect(Collectors.toSet());
		if (!coinsToUnsubscribe.isEmpty()) sendInBatches(symbolsToUnsubscribe, unsubscribeFrame, sender);
	}

	private <T extends GenericPublicWsPatch> void tryHandle(
					JsonNode root,
					Function<JsonNode, T> parser,
					Consumer<T> handler
	) {
		T patch = parser.apply(root);
		if (patch == null) return;
		handler.accept(patch);
	}

	private void handleFundingAndMarkMessage(String message) {
		if (message == null || message.isEmpty()) return;

		try {
			JsonNode root = mapper.readTree(message);
			tryHandle(root, messageHandler::parseMarkPriceMessageSymbol, this::handleFuturesMarkPricePatch);
			tryHandle(root, messageHandler::parseFundingRateMessageSymbol, this::handleFuturesFundingRatePatch);
		} catch (JsonProcessingException ignored) {
		}
	}

	private void handleBookTickerMessage(String message) {
		if (message == null || message.isEmpty()) return;

		try {
			JsonNode root = mapper.readTree(message);
			tryHandle(root, messageHandler::parseFuturesBookTickerMessageSymbol, this::handleFuturesBookTickerPatch);
		} catch (JsonProcessingException ignored) {
		}
	}

	private void handleSpotBookTickerMessage(String message) {
		if (message == null || message.isEmpty()) return;

		try {
			JsonNode root = mapper.readTree(message);
			tryHandle(root, messageHandler::parseSpotBookTickerMessageSymbol, this::handleSpotBookTickerPatch);
		} catch (JsonProcessingException ignored) {
		}
	}

	@Override
	public CompletableFuture<Void> connect() {
		this.fundingAndMarkClient.connect();
		this.bookTickerClient.connect();
		this.spotBookTickerClient.connect();
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void close() {
		this.fundingAndMarkClient.close();
		this.bookTickerClient.close();
		this.spotBookTickerClient.close();
	}

	@Override
	protected String getSpotPingFrame() {
		return null;
	}
}
