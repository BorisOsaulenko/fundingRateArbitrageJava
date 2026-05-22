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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public abstract class FullFundingViaRest extends PublicWsClient {
	private static final long POLL_INTERVAL_MS = 10_000;
	private final IModifiableScheduler fundingRateScheduler;
	private final PublicHttpClient httpClient;
	private final Logger log = LoggerFactory.getLogger(FullFundingViaRest.class);

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

		httpClient.getFundingRate(futuresFundingRateHandlers.keySet()).thenAccept((rates) -> {
			rates.forEach((coin, rate) -> {
				FundingRatePatch patch = new FundingRatePatch(coin, rate.rate(), rate.settlement(), rate.timestamp());

				Set<Consumer<FundingRatePatch>> handlers = futuresFundingRateHandlers.get(patch.coin());
				if (handlers != null) handlers.forEach(h -> h.accept(patch));
			});
		}).exceptionally(_ -> {
			log.warn("Funding rates update failed on this cycle");
			return null; // Imitate websocket behavior - if REST call fails, just skip this update cycle.
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
