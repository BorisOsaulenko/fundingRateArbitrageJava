package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class PublicWsClient implements PublicWsFrames, PublicMarketDataStream {
	protected final ExchangeContext context;
	protected final MessageHandler messageHandler;
	protected final CoinVector<PublicWsInstance> spotCoinToInstanceMap = new CoinVector<>();
	protected final CoinVector<PublicWsInstance> futuresCoinToInstanceMap = new CoinVector<>();
	private final ClientsConfig config;
	private final List<PublicWsInstance> spotClients;
	private final List<PublicWsInstance> futuresClients;
	private IModifiableScheduler futuresPingScheduler = null;
	private IModifiableScheduler spotPingScheduler = null;
	private int spotClientIndex = 0;
	private int futuresClientIndex = 0;

	public PublicWsClient(
					ExchangeContext context,
					ClientsConfig config,
					MessageHandler messageHandler,
					List<PublicWsInstance> spotClients,
					List<PublicWsInstance> futuresClients,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		this.context = context;
		this.messageHandler = messageHandler;
		this.config = config;
		this.spotClients = spotClients;
		this.futuresClients = futuresClients;
		if (config.spotPingIntervalMs() != 0)
			this.spotPingScheduler = schedulerBuilder.create(this::sendSpotPings, config.spotPingIntervalMs());
		if (config.futuresPingIntervalMs() != 0)
			this.futuresPingScheduler = schedulerBuilder.create(this::sendFuturesPings, config.futuresPingIntervalMs());
	}

	@Override
	public CompletableFuture<Void> connect() {
		spotClients.forEach(PublicWsInstance::connect);
		futuresClients.forEach(PublicWsInstance::connect);
		if (spotPingScheduler != null) spotPingScheduler.start();
		if (futuresPingScheduler != null) futuresPingScheduler.start();
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void close() {
		spotClients.forEach(PublicWsInstance::close);
		futuresClients.forEach(PublicWsInstance::close);
		spotPingScheduler.cancelNow();
		futuresPingScheduler.cancelNow();
	}

	private void sendSpotPings() {
		spotClients.forEach(PublicWsInstance::sendPing);
	}

	private void sendFuturesPings() {
		futuresClients.forEach(PublicWsInstance::sendPing);
	}

	private <T> int subscribe(
					Set<String> coinsToSub,
					CoinVector<PublicWsInstance> coinToInstanceMap,
					List<PublicWsInstance> clients,
					int index,
					Consumer<T> handler,
					TriConsumer<PublicWsInstance, Set<String>, Consumer<T>> subscribeFunction
	) {
		Map<PublicWsInstance, Set<String>> coinMap = new HashMap<>();
		for (String coin : coinsToSub) {
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
					Consumer<T> handler,
					TriConsumer<PublicWsInstance, Set<String>, Consumer<T>> subscribeFun
	) {
		futuresClientIndex =
						subscribe(coinsToSub, futuresCoinToInstanceMap, futuresClients, futuresClientIndex, handler, subscribeFun);
	}

	private <T> void subscribeSpot(
					Set<String> coinsToSub,
					Consumer<T> handler,
					TriConsumer<PublicWsInstance, Set<String>, Consumer<T>> subscribeFun
	) {
		spotClientIndex =
						subscribe(coinsToSub, spotCoinToInstanceMap, spotClients, spotClientIndex, handler, subscribeFun);
	}

	@Override
	public void subscribeFuturesFundingRates(Set<String> coinsToSub, Consumer<FundingRatePatch> handler) {
		subscribeFutures(coinsToSub, handler, PublicWsInstance::subscribeFuturesFundingRates);
	}

	@Override
	public void subscribeFuturesBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribeFutures(coins, handler, PublicWsInstance::subscribeFuturesBookTicker);
	}

	@Override
	public void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPricePatch> handler) {
		subscribeFutures(coins, handler, PublicWsInstance::subscribeFuturesMarkPrice);
	}

	@Override
	public void subscribeSpotBookTicker(Set<String> coins, Consumer<BookTickerPatch> handler) {
		subscribeSpot(coins, handler, PublicWsInstance::subscribeSpotBookTicker);
	}

	private void unsubscribeCoins(
					Set<String> coins,
					CoinVector<PublicWsInstance> coinToInstanceMap,
					BiConsumer<PublicWsInstance, Set<String>> unsubscribeFun
	) {
		Map<PublicWsInstance, Set<String>> coinMap = new HashMap<>();
		for (String coin : coins) {
			PublicWsInstance instance = coinToInstanceMap.remove(coin);
			if (instance != null) coinMap.computeIfAbsent(instance, _ -> new HashSet<>()).add(coin);
		}

		for (Map.Entry<PublicWsInstance, Set<String>> e : coinMap.entrySet()) {
			unsubscribeFun.accept(e.getKey(), e.getValue());
		}
	}

	@Override
	public void unsubscribeCoinsFutures(Set<String> coins) {
		unsubscribeCoins(coins, futuresCoinToInstanceMap, PublicWsInstance::unsubscribeCoinsFutures);
	}

	@Override
	public void unsubscribeCoinsSpot(Set<String> coins) {
		unsubscribeCoins(coins, spotCoinToInstanceMap, PublicWsInstance::unsubscribeCoinsSpot);
	}

	@Override
	public abstract String getSpotPingFrame();

	@Override
	public abstract String getFuturesPingFrame();

	@Override
	public abstract String getSubscribeFuturesFundingRateFrame(Set<String> symbols);

	@Override
	public abstract String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols);

	@Override
	public abstract String getSubscribeFuturesBookTickerFrame(Set<String> symbols);

	@Override
	public abstract String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols);

	@Override
	public abstract String getSubscribeFuturesMarkPriceFrame(Set<String> symbols);

	@Override
	public abstract String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols);

	@Override
	public abstract String getSubscribeSpotBookTickerFrame(Set<String> symbols);

	@Override
	public abstract String getUnsubscribeSpotBookTickerFrame(Set<String> symbols);
}
