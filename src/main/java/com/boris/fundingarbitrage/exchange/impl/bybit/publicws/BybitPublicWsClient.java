package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.prettyclient.PrettyWsClient;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;
import lombok.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class BybitPublicWsClient extends FullFundingViaRest {
	private static final URI futuresEndpoint = URI.create("wss://stream.bybit.com/v5/public/linear");
	private static final URI spotEndpoint = URI.create("wss://stream.bybit.com/v5/public/spot");
	private static final int spotClientsAmount = 10;
	private static final int maxSpotMessageCoinSize = 10;
	private final IModifiableScheduler pingScheduler;

	private final PrettyWsClient futuresClient;
	private final List<PrettyWsClient> spotClients;

	public BybitPublicWsClient(ExchangeContext context, BybitPublicHttpClient publicHttp) {
		BybitPublicMessageHandler messageHandler = new BybitPublicMessageHandler(context);
		IModifiableSchedulerBuilder schedulerBuilder = new ProdModifiableSchedulerBuilder();
		super(context, futuresEndpoint, messageHandler, publicHttp, schedulerBuilder);
		this.futuresClient = new PrettyWsClient(futuresEndpoint, "Bybit Public Futures", this::handleMessage);
		this.spotClients = new ArrayList<>();
		for (int i = 0; i < spotClientsAmount; i++) {
			spotClients.add(new PrettyWsClient(spotEndpoint, "Bybit Public Spot " + i, this::handleMessage));
		}
		pingScheduler = schedulerBuilder.create(this::sendPings, 20_000);
	}

	private String getSubscribeBookTickerFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "orderbook.1." + symbol).toList();
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeBookTickerFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "orderbook.1." + symbol).toList();
		return new WsRequest("unsubscribe", topics).toJson();
	}

	private String getSubscribeMarkPriceFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("subscribe", topics).toJson();
	}

	private String getUnsubscribeMarkPriceFrame(Set<String> symbols) {
		List<String> topics = symbols.stream().map(symbol -> "tickers." + symbol).toList();
		return new WsRequest("unsubscribe", topics).toJson();
	}

	@Override
	protected String getSubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols) {
		return null;
	}

	@Override
	protected String getSubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getSubscribeBookTickerFrame(symbols);
	}

	@Override
	protected String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeBookTickerFrame(symbols);
	}

	@Override
	protected String getSubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getSubscribeMarkPriceFrame(symbols);
	}

	@Override
	protected String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols) {
		return getUnsubscribeMarkPriceFrame(symbols);
	}

	@Override
	protected String getSubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getSubscribeBookTickerFrame(symbols);
	}

	@Override
	protected String getUnsubscribeSpotBookTickerFrame(Set<String> symbols) {
		return getUnsubscribeBookTickerFrame(symbols);
	}

	private List<Set<String>> splitCoins(Set<String> coins) {
		List<Set<String>> result = new ArrayList<>();

		Set<String> currentSet = new HashSet<>();
		int currentCounter = 0;
		for (String coin : coins) {
			currentSet.add(coin);
			currentCounter++;
			if (currentCounter >= maxSpotMessageCoinSize) {
				result.add(currentSet);
				currentSet = new HashSet<>();
				currentCounter = 0;
			}
		}

		if (!currentSet.isEmpty()) {
			result.add(currentSet);
		}

		return result;
	}

	@Override
	public void subscribeSpotBookTicker(Set<String> coins, Consumer<@NonNull BookTickerPatch> handler) {
		System.out.println("Subscribing to spot book ticker for coins: " + coins);
		int idx = 0;
		for (Set<String> coinSet : splitCoins(coins)) {
			super.addHandlers(
							coinSet,
							spotBookTickerHandlers,
							handler,
							context::getSpotSymbol,
							this::getSubscribeSpotBookTickerFrame,
							spotClients.get(idx++ % spotClientsAmount)::sendMessage
			);
		}
	}

	@Override
	public void unsubscribeSpotBookTicker(Set<String> coins) {
		int idx = 0;
		for (Set<String> coinSet : splitCoins(coins))
			super.removeHandlers(
							coinSet,
							spotBookTickerHandlers,
							context::getSpotSymbol,
							this::getUnsubscribeSpotBookTickerFrame,
							spotClients.get(idx++ % spotClientsAmount)::sendMessage
			);
	}

	@Override
	public CompletableFuture<Void> connect() {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		futures.add(CompletableFuture.runAsync(futuresClient::connect));
		spotClients.forEach(client -> futures.add(CompletableFuture.runAsync(client::connect)));
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	@Override
	public void close() {
		futuresClient.close();
		spotClients.forEach(PrettyWsClient::close);
		pingScheduler.cancelNow();
	}

	private void sendPings() {
		String pingMsg = "{\"op\": \"ping\"}";
		futuresClient.sendMessage(pingMsg);
		spotClients.forEach(client -> client.sendMessage(pingMsg));
	}

	@Override
	protected String getSpotPingFrame() {
		return null;
	}
}
