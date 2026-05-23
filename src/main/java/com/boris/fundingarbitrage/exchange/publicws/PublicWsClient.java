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

@Slf4j
public abstract class PublicWsClient implements IPublicMarketDataStream {
	protected final SpotChannels spot;
	protected final FuturesChannels futures;
	private final IModifiableSchedulerBuilder schedulerBuilder;
	private final ClientsConfig config;

	public PublicWsClient(
					ClientsConfig config,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		this.schedulerBuilder = schedulerBuilder;
		this.config = config;

		spot = new SpotChannels(
						createChannelState(config.spotBookTicker())
		);

		futures = new FuturesChannels(
						createChannelState(config.futuresBookTicker()),
						createChannelState(config.futuresFunding()),
						createChannelState(config.futuresMark())
		);

		initDomainClients(config.spotBookTicker(), spot.bookTicker());
		initDomainClients(config.futuresBookTicker(), futures.bookTicker());
		initDomainClients(config.futuresFunding(), futures.funding());
		initDomainClients(config.futuresMark(), futures.mark());
	}

	private <T extends GenericPublicWsPatch> ChannelState<T> createChannelState(DomainClientConfig<T> config) {
		ArrayList<PublicWsInstance<T>> clients = new ArrayList<>();
		return new ChannelState<>(
						new CoinVector<>(),
						getPingScheduler(config, clients),
						new CoinVector<>(),
						clients,
						new AtomicInteger(0)
		);
	}

	private <T extends GenericPublicWsPatch> IModifiableScheduler getPingScheduler(
					DomainClientConfig<T> config,
					List<PublicWsInstance<T>> clients
	) {
		long pingInterval = config.orchestratorConfig().pingIntervalMs();
		if (pingInterval == 0) return null;
		return schedulerBuilder.create(() -> clients.forEach(PublicWsInstance::sendPing), pingInterval);
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
		closeChannels(spot.states());
		closeChannels(futures.states());
	}

	private void closeChannels(List<ChannelState<? extends GenericPublicWsPatch>> states) {
		for (ChannelState<? extends GenericPublicWsPatch> state : states) {
			state.clients().forEach(PublicWsInstance::close);
			if (state.pingScheduler() != null) state.pingScheduler().cancelNow();
		}
	}

	private <T extends GenericPublicWsPatch> void subscribe(
					Set<String> coinsToSub,
					Consumer<T> handler,
					ChannelState<T> channelState
	) {
		var handlers = channelState.handlers();
		var clients = channelState.clients();
		var index = channelState.index().get();
		var coinToInstanceMap = channelState.coinToInstanceMap();

		Map<PublicWsInstance<T>, Set<String>> coinMap = new HashMap<>();
		for (String coin : coinsToSub) {
			if (handlers.containsKey(coin)) throw new RuntimeException("Coin " + coin + " is already subscribed.");
			handlers.put(coin, handler);

			PublicWsInstance<T> client = clients.get(index++ % clients.size());
			coinToInstanceMap.put(coin, client);
			coinMap.computeIfAbsent(client, _ -> new HashSet<>()).add(coin);
		}

		for (Map.Entry<PublicWsInstance<T>, Set<String>> e : coinMap.entrySet()) {
			e.getKey().subscribe(e.getValue(), handler);
		}

		channelState.index().set(index);
	}

	protected void subscribeFuturesFundingRates(Set<String> coins, Consumer<FundingPatch> handler) {
		subscribe(coins, handler, futures.funding());
	}

	protected void subscribeFuturesBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribe(coins, handler, futures.bookTicker());
	}

	protected void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPatch> handler) {
		subscribe(coins, handler, futures.mark());
	}

	protected void subscribeSpotBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribe(coins, handler, spot.bookTicker());
	}

	private boolean presentOnFutures(String coin) {
		return futures.funding().handlers().containsKey(coin);
	}

	private boolean presentOnSpot(String coin) {
		return spot.bookTicker().handlers().containsKey(coin);
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

	protected void unsubscribeSpotBookTicker(Set<String> coins) {
		unsubscribeCoins(coins, spot.bookTicker());
	}

	protected void unsubscribeFuturesBookTicker(Set<String> coins) {
		unsubscribeCoins(coins, futures.bookTicker());
	}

	protected void unsubscribeFuturesFunding(Set<String> coins) {
		unsubscribeCoins(coins, futures.funding());
	}

	protected void unsubscribeFuturesMark(Set<String> coins) {
		unsubscribeCoins(coins, futures.mark());
	}

	@Override
	public void unsubscribeCoinsFutures(Set<String> coins) {
		unsubscribeFuturesBookTicker(coins);
		unsubscribeFuturesFunding(coins);
		unsubscribeFuturesMark(coins);
	}

	@Override
	public void unsubscribeCoinsSpot(Set<String> coins) {
		unsubscribeSpotBookTicker(coins);
	}

	protected record SpotChannels(ChannelState<BookTickerPatch> bookTicker) {
		private List<ChannelState<? extends GenericPublicWsPatch>> states() {
			return List.of(bookTicker);
		}
	}

	protected record FuturesChannels(
					ChannelState<BookTickerPatch> bookTicker,
					ChannelState<FundingPatch> funding,
					ChannelState<MarkPatch> mark
	) {
		private List<ChannelState<? extends GenericPublicWsPatch>> states() {
			return List.of(bookTicker, funding, mark);
		}
	}
}
