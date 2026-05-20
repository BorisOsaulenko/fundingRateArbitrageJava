package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.IMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public abstract class FullFundingViaRest extends PublicWsClient {
	private static final long POLL_INTERVAL_MS = 10_000;
	private final IModifiableScheduler fundingRateScheduler;
	private final PublicHttpClient httpClient;

	public FullFundingViaRest(
					ExchangeContext context,
					ClientsConfig config,
					IPublicWsFrames wsFrames,
					IMessageHandler messageHandler,
					PublicHttpClient publicHttp,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(context, config, wsFrames, messageHandler, schedulerBuilder);
		this.httpClient = publicHttp;
		fundingRateScheduler = schedulerBuilder.create(this::pollFundingRates, POLL_INTERVAL_MS);
		fundingRateScheduler.start();
	}

	private void pollFundingRates() {
		if (futuresFundingRateHandlers.isEmpty()) return;

		httpClient.getFundingRate(futuresFundingRateHandlers.keySet()).whenComplete((rates, err) -> {
			if (err != null) return; // Imitate websocket behavior - if REST call fails, just skip this update cycle.
			rates.forEach((coin, rate) -> {
				if (rate == null) return;
				FundingRatePatch patch = new FundingRatePatch(coin, rate.rate(), rate.settlement(), rate.timestamp());

				Set<Consumer<FundingRatePatch>> handlers = futuresFundingRateHandlers.get(patch.coin());
				if (handlers != null) {
					for (Consumer<FundingRatePatch> handler : handlers) handler.accept(patch);
				}
			});
		});
	}

	@Override
	public void subscribeFuturesFundingRates(Set<String> coinsToSub, Consumer<FundingRatePatch> handler) {
		coinsToSub.forEach(coin -> futuresFundingRateHandlers.computeIfAbsent(coin, _ -> new HashSet<>())
						.add(handler));
	}

	@Override
	public void close() {
		super.close();
		fundingRateScheduler.cancelNow();
	}
}
