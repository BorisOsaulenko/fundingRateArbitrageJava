package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.scheduler.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

public abstract class FullFundingViaRest extends PublicWsClient {
	private static final long POLL_INTERVAL_MS = 10_000;
	private final IModifiableScheduler fundingRateScheduler;
	private final PublicHttpClient httpClient;
	private final Logger log = LoggerFactory.getLogger(FullFundingViaRest.class);

	private final CoinVector<Consumer<FundingPatch>> fundingRateHandlers = new CoinVector<>();

	public FullFundingViaRest(
					ClientsConfigNoFunding configNoFunding,
					PublicHttpClient publicHttp,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		ClientsConfig config = new ClientsConfig(
						configNoFunding.futuresBookTicker(),
						null, // No funding ws client
						configNoFunding.futuresMark(),
						configNoFunding.spotBookTicker()
		);
		super(config, schedulerBuilder);
		this.httpClient = publicHttp;
		fundingRateScheduler = schedulerBuilder.create(this::pollFundingRates, POLL_INTERVAL_MS);
		fundingRateScheduler.start();
	}

	private void pollFundingRates() {
		if (fundingRateHandlers.isEmpty()) return;

		httpClient.getFundingRate(fundingRateHandlers.keySet()).thenAccept((rates) -> {
			rates.forEach((coin, rate) -> {
				FundingPatch patch = new FundingPatch(coin, rate.rate(), rate.settlement(), rate.timestamp());

				Consumer<FundingPatch> handler = fundingRateHandlers.get(patch.coin());
				if (handler != null) handler.accept(patch);
			});
		}).exceptionally(_ -> {
			log.warn("Funding rates update failed on this cycle");
			return null; // Imitate websocket behavior - if REST call fails, just skip this update cycle.
		});
	}

	@Override
	protected boolean presentOnFutures(String coin) {
		return this.fundingRateHandlers.containsKey(coin);
	}

	@Override
	protected void subscribeFuturesFundingRates(Set<String> coins, Consumer<FundingPatch> handler) {
		for (String coin : coins) {
			if (fundingRateHandlers.containsKey(coin))
				throw new RuntimeException("Already subscribed to funding rates for " + coin);
			fundingRateHandlers.put(coin, handler);
		}
	}

	@Override
	protected void unsubscribeFuturesFunding(Set<String> coins) {
		fundingRateHandlers.removeAll(coins);
	}

	@Override
	public void close() {
		super.close();
		fundingRateScheduler.cancelNow();
	}
}
