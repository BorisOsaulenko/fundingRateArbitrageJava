package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

public abstract class FullFundingViaRest extends PublicWsClient {
	private static final long POLL_INTERVAL_MS = 10_000;
	private final IModifiableScheduler fundingRateScheduler;
	private final PublicHttpClient httpClient;
	private final Logger log = LoggerFactory.getLogger(FullFundingViaRest.class);

	public FullFundingViaRest(
					ClientsConfig config,
					PublicHttpClient publicHttp,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(config, schedulerBuilder);
		this.httpClient = publicHttp;
		fundingRateScheduler = schedulerBuilder.create(this::pollFundingRates, POLL_INTERVAL_MS);
		fundingRateScheduler.start();
	}

	private void pollFundingRates() {
		var handlers = futures.funding().handlers();
		if (handlers.isEmpty()) return;

		httpClient.getFundingRate(handlers.keySet()).thenAccept((rates) -> {
			rates.forEach((coin, rate) -> {
				FundingPatch patch = new FundingPatch(coin, rate.rate(), rate.settlement(), rate.timestamp());

				Consumer<FundingPatch> handler = handlers.get(patch.coin());
				if (handler != null) handler.accept(patch);
			});
		}).exceptionally(_ -> {
			log.warn("Funding rates update failed on this cycle");
			return null; // Imitate websocket behavior - if REST call fails, just skip this update cycle.
		});
	}

	@Override
	protected void subscribeFuturesFundingRates(Set<String> coins, Consumer<FundingPatch> handler) {
		var handlers = futures.funding().handlers();
		for (String coin : coins) {
			if (handlers.containsKey(coin))
				throw new RuntimeException("Already subscribed to funding rates for " + coin);
			handlers.put(coin, handler);
		}
	}

	@Override
	protected void unsubscribeFuturesFunding(Set<String> coins) {
		var handlers = futures.funding().handlers();
		handlers.removeAll(coins);
	}

	@Override
	public void close() {
		super.close();
		fundingRateScheduler.cancelNow();
	}
}
