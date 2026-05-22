package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public abstract class PublicWsClient implements IPublicMarketDataStream {
	private final IModifiableSchedulerBuilder schedulerBuilder;
	private final ClientsConfig config;
	protected ChannelState<BookTickerPatch> spotBookTickerState;
	protected ChannelState<BookTickerPatch> futuresBookTickerState;
	protected ChannelState<FundingPatch> futuresFundingState;
	protected ChannelState<MarkPatch> futuresMarkState;

	public PublicWsClient(
					ClientsConfig config,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		this.schedulerBuilder = schedulerBuilder;
		this.config = config;

		spotBookTickerState = new ChannelState<>(
						new CoinVector<>(),
						getPingScheduler(config.spotBookTicker(), () -> this.spotBookTickerState),
						new CoinVector<>(),
						new ArrayList<>(),
						new AtomicInteger(0)
		);

		futuresBookTickerState = new ChannelState<>(
						new CoinVector<>(),
						getPingScheduler(config.futuresBookTicker(), () -> this.futuresBookTickerState),
						new CoinVector<>(),
						new ArrayList<>(),
						new AtomicInteger(0)
		);

		futuresFundingState = new ChannelState<>(
						new CoinVector<>(),
						getPingScheduler(config.futuresFunding(), () -> this.futuresFundingState),
						new CoinVector<>(),
						new ArrayList<>(),
						new AtomicInteger(0)
		);

		futuresMarkState = new ChannelState<>(
						new CoinVector<>(),
						getPingScheduler(config.futuresMark(), () -> this.futuresMarkState),
						new CoinVector<>(),
						new ArrayList<>(),
						new AtomicInteger(0)
		);

		initDomainClients(config.spotBookTicker(), spotBookTickerState);
		initDomainClients(config.futuresBookTicker(), futuresBookTickerState);
		initDomainClients(config.futuresFunding(), futuresFundingState);
		initDomainClients(config.futuresMark(), futuresMarkState);
	}

	private <T extends GenericPublicWsPatch> IModifiableScheduler getPingScheduler(
					DomainClientConfig<T> config,
					Supplier<ChannelState<T>> state
	) {
		long pingInterval = config.orchestratorConfig().pingIntervalMs();
		if (pingInterval == 0) return null;
		return schedulerBuilder.create(() -> state.get().clients().forEach(PublicWsInstance::sendPing), pingInterval);
	}

	private <T extends GenericPublicWsPatch> void initDomainClients(
					DomainClientConfig<T> domainClientConfig,
					ChannelState<T> channelState
	) {
		domainClientConfig.endpoint().thenAccept(endpoint -> {
			for (int i = 0; i < domainClientConfig.orchestratorConfig().clientsAmount(); i++) {
				PublicWsInstance<T> instance = new PublicWsInstance<>(endpoint, domainClientConfig.instanceConfig());
				channelState.clients().add(instance);
			}

			channelState.clients().parallelStream().forEach(PublicWsInstance::connect);
			if (channelState.pingScheduler() != null) channelState.pingScheduler().start();
		});
	}

	@Override
	public CompletableFuture<Void> connect() {
		List<CompletableFuture<URI>> connectFutures = new ArrayList<>();
		connectFutures.add(config.spotBookTicker().endpoint());
		connectFutures.add(config.futuresBookTicker().endpoint());
		connectFutures.add(config.futuresFunding().endpoint());
		connectFutures.add(config.futuresMark().endpoint());
		return CompletableFuture.allOf(connectFutures.toArray(new CompletableFuture[0]));
	}

	@Override
	public void close() {
		spotBookTickerState.clients().forEach(PublicWsInstance::close);
		futuresBookTickerState.clients().forEach(PublicWsInstance::close);
		futuresFundingState.clients().forEach(PublicWsInstance::close);
		futuresMarkState.clients().forEach(PublicWsInstance::close);
	}

	private <T extends GenericPublicWsPatch> void subscribe(
					Set<String> coinsToSub,
					Consumer<T> handler,
					ChannelState<T> channelState
	) {
		var handlers = channelState.handlers();
		var clients = channelState.clients();
		var index = channelState.index().get();

		Map<PublicWsInstance<T>, Set<String>> coinMap = new HashMap<>();
		for (String coin : coinsToSub) {
			if (handlers.containsKey(coin)) throw new RuntimeException("Coin " + coin + " is already subscribed.");
			handlers.put(coin, handler);

			PublicWsInstance<T> client = clients.get(index++ % clients.size());
			coinMap.computeIfAbsent(client, _ -> new HashSet<>()).add(coin);
		}

		for (Map.Entry<PublicWsInstance<T>, Set<String>> e : coinMap.entrySet()) {
			e.getKey().subscribe(e.getValue(), handler);
		}

		channelState.index().set(index);
	}

	protected void subscribeFuturesFundingRates(Set<String> coins, Consumer<FundingPatch> handler) {
		subscribe(coins, handler, futuresFundingState);
	}

	protected void subscribeFuturesBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribe(coins, handler, futuresBookTickerState);
	}

	protected void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPatch> handler) {
		subscribe(coins, handler, futuresMarkState);
	}

	protected void subscribeSpotBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribe(coins, handler, spotBookTickerState);
	}

	private boolean presentOnFutures(String coin) {
		return futuresFundingState.handlers().containsKey(coin);
	}

	private boolean presentOnSpot(String coin) {
		return spotBookTickerState.handlers().containsKey(coin);
	}

	@Override
	public void subscribeFutures(Set<String> coins, FuturesHandler handler) {
		Optional<String> alreadySubscribed = coins.stream().filter(this::presentOnFutures).findFirst();
		if (alreadySubscribed.isPresent())
			throw new RuntimeException("Coin " + alreadySubscribed.get() + " is already subscribed to futures.");
		subscribeFuturesFundingRates(coins, handler.fundingRateHandler());
		subscribeFuturesMarkPrice(coins, handler.markPriceHandler());
		subscribeFuturesBookTicker(coins, handler.bookTickerHandler());
	}

	@Override
	public void subscribeSpot(Set<String> coins, SpotHandler handler) {
		Optional<String> alreadySubscribed = coins.stream().filter(this::presentOnSpot).findFirst();
		if (alreadySubscribed.isPresent())
			throw new RuntimeException("Coin " + alreadySubscribed.get() + " is already subscribed to spot.");
		subscribeSpotBookTicker(coins, handler.bookTickerHandler());
	}

	private <T extends GenericPublicWsPatch> void unsubscribeCoins(
					Set<String> coins,
					ChannelState<T> channelState
	) {
		var coinToInstanceMap = channelState.coinToInstanceMap();

		Map<PublicWsInstance<T>, Set<String>> coinMap = new HashMap<>();
		for (String coin : coins) {
			PublicWsInstance<T> instance = coinToInstanceMap.remove(coin);
			if (instance == null) continue;
			coinMap.computeIfAbsent(instance, _ -> new HashSet<>()).add(coin);
		}

		for (Map.Entry<PublicWsInstance<T>, Set<String>> e : coinMap.entrySet()) {
			e.getKey().unsubscribe(e.getValue());
		}
	}

	@Override
	public void unsubscribeCoinsFutures(Set<String> coins) {
		unsubscribeCoins(coins, futuresFundingState);
		unsubscribeCoins(coins, futuresMarkState);
		unsubscribeCoins(coins, futuresBookTickerState);
	}

	@Override
	public void unsubscribeCoinsSpot(Set<String> coins) {
		unsubscribeCoins(coins, spotBookTickerState);
	}
}
