package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
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
	private final CoinVector<Boolean> updatingFundingRateVector = new CoinVector<>();
	private final CoinVector<CompletableFuture<FundingRate>> fundingRateFutureVector = new CoinVector<>();

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
		for (String coin : coins) {
			if (Boolean.TRUE.equals(updatingFundingRateVector.get(coin))) {
				continue;
			}

			updatingFundingRateVector.put(coin, true);
			CompletableFuture<FundingRate> future = publicHttpClient.getFundingRate(coin);
			fundingRateFutureVector.put(coin, future);
			future.whenComplete((fundingRate, err) -> {
				updatingFundingRateVector.put(coin, false);
				if (err != null || fundingRate == null) return;
				FundingRatePatch patch = new FundingRatePatch(
								coin,
								fundingRate.rate(),
								fundingRate.settlement(),
								fundingRate.timestamp()
				);
				dispatchPatchToHandlers(patch, fundingRateHandlers);
			});
		}
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
