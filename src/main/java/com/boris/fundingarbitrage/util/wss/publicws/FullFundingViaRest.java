package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class FullFundingViaRest extends PublicWsClient {
	private static final long POLL_INTERVAL_SECONDS = 10;
	private final ScheduledExecutorService fundingRateExecutor = Executors.newSingleThreadScheduledExecutor();

	public FullFundingViaRest(
					ExchangeContext context,
					URI endpoint,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttp
	) {
		super(context, endpoint, messageHandler, publicHttp);
		fundingRateExecutor.scheduleAtFixedRate(this::pollFundingRates, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public FullFundingViaRest(
					ExchangeContext context,
					CompletableFuture<URI> endpointFuture,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttp
	) {
		super(context, endpointFuture, messageHandler, publicHttp);
		fundingRateExecutor.scheduleAtFixedRate(this::pollFundingRates, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	private void pollFundingRates() {
		if (fundingRateHandlers.isEmpty()) return;
		List<String> coins = new ArrayList<>(fundingRateHandlers.keySet());

		publicHttpClient.getFundingRate(coins).whenComplete((rates, err) -> {
			if (err != null) return; // Imitate websocket behavior - if REST call fails, just skip this update cycle.
			rates.forEach((coin, rate) -> {
				if (rate == null) return;
				FundingRatePatch patch = new FundingRatePatch(coin, rate.rate, rate.settlement, rate.timestamp);
				dispatchPatchToHandlers(patch, fundingRateHandlers);
			});
		});
	}


	@Override
	protected void handleFundingRatePatch(@NotNull FundingRatePatch patch) {
		// Full funding updates are sourced from REST polling.
	}

	@Override
	public void close() {
		super.close();
		fundingRateExecutor.shutdownNow();
	}
}
