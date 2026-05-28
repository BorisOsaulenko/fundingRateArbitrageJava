package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableScheduler;
import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;

public abstract class FundingSettlementViaRest extends PublicWsClient {
	private final CoinVector<Instant> settlementVector = new CoinVector<>();
	private final PublicHttpClient publicHttp;
	private final IModifiableScheduler fundingSettlementScheduler;
	private final Logger log = LoggerFactory.getLogger(FundingSettlementViaRest.class);

	public FundingSettlementViaRest(
					ClientsConfig config,
					PublicHttpClient publicHttp,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(config, schedulerBuilder);
		this.publicHttp = publicHttp;
		this.fundingSettlementScheduler = schedulerBuilder.create(this::updateFundingSettlementForCoins, 10_000);
		this.fundingSettlementScheduler.start();
	}

	private void updateFundingSettlementForCoins() {
		var handlers = futures.funding().handlers();
		if (handlers.isEmpty()) return;

		publicHttp.getFundingRate(handlers.keySet())
						.thenAccept((rates) -> rates.forEach((coin, rate) -> settlementVector.put(coin, rate.settlement())))
						.exceptionally(_ -> {
							log.warn("Funding settlements update failed on this cycle");
							return null; // Imitate websocket behavior - if REST call fails, just skip this update cycle.
						});
	}

	@Override
	public void subscribeFuturesFundingRates(Set<String> coins, Consumer<FundingPatch> handler) {
		Consumer<FundingPatch> handlerWithSettlement = (patch) -> handler.accept(new FundingPatch(
						patch.coin(),
						patch.rate(),
						settlementVector.get(patch.coin()),
						patch.timestamp()
		));
		super.subscribeFuturesFundingRates(coins, handlerWithSettlement);
	}

	@Override
	public void close() {
		super.close();
		fundingSettlementScheduler.cancelNow();
	}
}
