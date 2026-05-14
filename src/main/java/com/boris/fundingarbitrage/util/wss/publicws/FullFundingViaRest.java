package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public abstract class FullFundingViaRest extends PublicWsClient {
	private static final long POLL_INTERVAL_MS = 10_000;
	private final IModifiableScheduler fundingRateScheduler;

	public FullFundingViaRest(
					ExchangeContext context,
					URI endpoint,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttp,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(context, endpoint, messageHandler, publicHttp, schedulerBuilder);
		fundingRateScheduler = schedulerBuilder.create(this::pollFundingRates, POLL_INTERVAL_MS);
	}

	public FullFundingViaRest(
					ExchangeContext context,
					CompletableFuture<URI> endpointFuture,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttp,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(context, endpointFuture, messageHandler, publicHttp, schedulerBuilder);
		fundingRateScheduler = schedulerBuilder.create(this::pollFundingRates, POLL_INTERVAL_MS * 1000L);
	}

	private void pollFundingRates() {
		if (futuresFundingRateHandlers.isEmpty()) return;

		publicHttpClient.getFundingRate(futuresFundingRateHandlers.keySet()).whenComplete((rates, err) -> {
			if (err != null) return; // Imitate websocket behavior - if REST call fails, just skip this update cycle.
			rates.forEach((coin, rate) -> {
				if (rate == null) return;
				FundingRatePatch patch = new FundingRatePatch(coin, rate.rate(), rate.settlement(), rate.timestamp());
				dispatchPatchToHandlers(patch, futuresFundingRateHandlers);
			});
		});
	}


	@Override
	protected void handleFuturesFundingRatePatch(@NotNull FundingRatePatch patch) {
		// Full funding updates are sourced from REST polling.
	}

	@Override
	public void close() {
		super.close();
		fundingRateScheduler.cancelNow();
	}
}
