package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.apache.commons.lang3.function.TriConsumer;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class PublicWsClient implements IPublicMarketDataStream {
	protected final CoinVector<PublicWsInstance> spotCoinToInstanceMap = new CoinVector<>();
	protected final CoinVector<PublicWsInstance> futuresCoinToInstanceMap = new CoinVector<>();
	protected final CoinVector<Set<Consumer<FundingRatePatch>>> futuresFundingRateHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<BookTickerPatch>>> futuresBookTickerHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<MarkPricePatch>>> futuresMarkPriceHandlers = new CoinVector<>();
	protected final CoinVector<Set<Consumer<BookTickerPatch>>> spotBookTickerHandlers = new CoinVector<>();
	private final ExchangeContext context;
	private final ClientsConfig config;
	private final IMessageHandler messageHandler;
	private final IPublicWsFrames wsFrames;
	private final CompletableFuture<Void> spotReadyFuture;
	private final CompletableFuture<Void> futuresReadyFuture;
	private final List<PublicWsInstance> spotClients = new ArrayList<>();
	private final List<PublicWsInstance> futuresClients = new ArrayList<>();
	private IModifiableScheduler futuresPingScheduler = null;
	private IModifiableScheduler spotPingScheduler = null;
	private int spotClientIndex = 0;
	private int futuresClientIndex = 0;

	public PublicWsClient(
					ExchangeContext context,
					ClientsConfig config,
					IPublicWsFrames wsFrames,
					IMessageHandler messageHandler,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		this.context = context;
		this.config = config;
		this.wsFrames = wsFrames;
		this.messageHandler = messageHandler;
		this.spotReadyFuture = config.spotEndpointFuture().thenAccept((uri) -> initClients(uri, TradeMarket.SPOT));
		this.futuresReadyFuture = config.futuresEndpointFuture().thenAccept((uri) -> initClients(uri, TradeMarket.FUTURES));

		if (config.spotPingIntervalSeconds() != 0)
			this.spotPingScheduler = schedulerBuilder.create(this::sendSpotPings, config.spotPingIntervalMs());
		if (config.futuresPingIntervalSeconds() != 0)
			this.futuresPingScheduler = schedulerBuilder.create(this::sendFuturesPings, config.futuresPingIntervalMs());
	}

	private void initClients(URI endpoint, TradeMarket market) {
		int clientsAmount = switch (market) {
			case SPOT -> config.spotClientsAmount();
			case FUTURES -> config.futuresClientsAmount();
		};

		int maxCoinRequest = switch (market) {
			case SPOT -> config.spotRequestMaxCoinSize();
			case FUTURES -> config.futuresRequestMaxCoinSize();
		};

		List<PublicWsInstance> clients = switch (market) {
			case SPOT -> spotClients;
			case FUTURES -> futuresClients;
		};

		for (int i = 0; i < clientsAmount; i++) {
			PublicWsInstance instance = new PublicWsInstance(
							context,
							endpoint,
							messageHandler,
							wsFrames,
							maxCoinRequest,
							market
			);
			clients.add(instance);
		}
	}

	@Override
	public CompletableFuture<Void> connect() {
		List<CompletableFuture<Void>> connectFutures = new ArrayList<>();
		connectFutures.add(spotReadyFuture.thenRun(() -> {
			spotClients.parallelStream().forEach(PublicWsInstance::connect);
			if (spotPingScheduler != null) spotPingScheduler.start();
		}));
		connectFutures.add(futuresReadyFuture.thenRun(() -> {
			futuresClients.parallelStream().forEach(PublicWsInstance::connect);
			if (futuresPingScheduler != null) futuresPingScheduler.start();
		}));
		return CompletableFuture.allOf(connectFutures.toArray(new CompletableFuture[0]));
	}

	@Override
	public void close() {
		spotClients.forEach(PublicWsInstance::close);
		futuresClients.forEach(PublicWsInstance::close);
		if (spotPingScheduler != null) spotPingScheduler.cancelNow();
		if (futuresPingScheduler != null) futuresPingScheduler.cancelNow();
	}

	private void sendSpotPings() {
		spotClients.forEach(PublicWsInstance::sendPing);
	}

	private void sendFuturesPings() {
		futuresClients.forEach(PublicWsInstance::sendPing);
	}

	private <T> int subscribe(
					Set<String> coinsToSub,
					CoinVector<Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					CoinVector<PublicWsInstance> coinToInstanceMap,
					List<PublicWsInstance> clients,
					int index,
					TriConsumer<PublicWsInstance, Set<String>, Consumer<T>> subscribeFunction
	) {
		Map<PublicWsInstance, Set<String>> coinMap = new HashMap<>();
		for (String coin : coinsToSub) {
			handlersMap.computeIfAbsent(coin, _ -> new CopyOnWriteArraySet<>()).add(handler);

			PublicWsInstance existing = coinToInstanceMap.get(coin);
			if (existing != null) {
				coinMap.computeIfAbsent(existing, _ -> new HashSet<>()).add(coin);
			} else {
				PublicWsInstance client = clients.get(index++ % clients.size());
				coinMap.computeIfAbsent(client, _ -> new HashSet<>()).add(coin);
				coinToInstanceMap.put(coin, client);
			}
		}

		for (Map.Entry<PublicWsInstance, Set<String>> e : coinMap.entrySet()) {
			subscribeFunction.accept(e.getKey(), e.getValue(), handler);
		}

		return index;
	}

	private <T> void subscribeFutures(
					Set<String> coinsToSub,
					CoinVector<Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					TriConsumer<PublicWsInstance, Set<String>, Consumer<T>> subscribeFun
	) {
		if (futuresReadyFuture.state() != Future.State.SUCCESS)
			throw new RuntimeException("Futures clients are not yet initialized. Call .connect().join() first");
		futuresClientIndex =
						subscribe(
										coinsToSub,
										handlersMap,
										handler,
										futuresCoinToInstanceMap,
										futuresClients,
										futuresClientIndex,
										subscribeFun
						);
	}

	private <T> void subscribeSpot(
					Set<String> coinsToSub,
					CoinVector<Set<Consumer<T>>> handlersMap,
					Consumer<T> handler,
					TriConsumer<PublicWsInstance, Set<String>, Consumer<T>> subscribeFun
	) {
		if (spotReadyFuture.state() != Future.State.SUCCESS)
			throw new RuntimeException("Spot clients are not yet initialized. Call .connect().join() first");
		spotClientIndex =
						subscribe(
										coinsToSub,
										handlersMap,
										handler,
										spotCoinToInstanceMap,
										spotClients,
										spotClientIndex,
										subscribeFun
						);
	}

	@Override
	public void subscribeFuturesFundingRates(Set<String> coinsToSub, Consumer<FundingRatePatch> handler) {
		subscribeFutures(coinsToSub, futuresFundingRateHandlers, handler, PublicWsInstance::subscribeFuturesFundingRates);
	}

	@Override
	public void subscribeFuturesBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribeFutures(coins, futuresBookTickerHandlers, handler, PublicWsInstance::subscribeFuturesBookTicker);
	}

	@Override
	public void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPricePatch> handler) {
		subscribeFutures(coins, futuresMarkPriceHandlers, handler, PublicWsInstance::subscribeFuturesMarkPrice);
	}

	@Override
	public void subscribeSpotBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribeSpot(coins, spotBookTickerHandlers, handler, PublicWsInstance::subscribeSpotBookTicker);
	}

	private void unsubscribeCoins(
					Set<String> coins,
					CoinVector<PublicWsInstance> coinToInstanceMap,
					BiConsumer<PublicWsInstance, Set<String>> unsubscribeFun
	) {
		Map<PublicWsInstance, Set<String>> coinMap = new HashMap<>();
		for (String coin : coins) {
			PublicWsInstance instance = coinToInstanceMap.remove(coin);
			if (instance == null) continue;
			coinMap.computeIfAbsent(instance, _ -> new HashSet<>()).add(coin);
		}

		for (Map.Entry<PublicWsInstance, Set<String>> e : coinMap.entrySet()) {
			unsubscribeFun.accept(e.getKey(), e.getValue());
		}
	}

	@Override
	public void unsubscribeCoinsFutures(Set<String> coins) {
		unsubscribeCoins(coins, futuresCoinToInstanceMap, PublicWsInstance::unsubscribeCoinsFutures);
		futuresMarkPriceHandlers.removeAll(coins);
		futuresBookTickerHandlers.removeAll(coins);
		futuresFundingRateHandlers.removeAll(coins);
	}

	@Override
	public void unsubscribeCoinsSpot(Set<String> coins) {
		unsubscribeCoins(coins, spotCoinToInstanceMap, PublicWsInstance::unsubscribeCoinsSpot);
		spotBookTickerHandlers.removeAll(coins);
	}
}
