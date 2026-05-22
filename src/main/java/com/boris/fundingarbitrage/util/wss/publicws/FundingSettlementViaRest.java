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
					ExchangeContext context,
					ClientsConfig config,
					IPublicWsFrames wsFrames,
					IMessageHandler messageHandler,
					PublicHttpClient publicHttp,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(context, config, wsFrames, messageHandler, schedulerBuilder);
		this.publicHttp = publicHttp;
		this.fundingSettlementScheduler = schedulerBuilder.create(this::updateFundingSettlementForCoins, 10_000);
		this.fundingSettlementScheduler.start();
	}

	private void updateFundingSettlementForCoins() {
		if (futuresFundingRateHandlers.isEmpty()) return;

		publicHttp.getFundingRate(futuresFundingRateHandlers.keySet())
						.thenAccept((rates) -> rates.forEach((coin, rate) -> settlementVector.put(coin, rate.settlement())))
						.exceptionally(_ -> {
							log.warn("Funding settlements update failed on this cycle");
							return null; // Imitate websocket behavior - if REST call fails, just skip this update cycle.
						});
	}

	@Override
	public void subscribeFuturesFundingRates(Set<String> coinsToSub, Consumer<FundingRatePatch> handler) {
		Consumer<FundingRatePatch> handlerWithSettlement = (patch) -> handler.accept(new FundingRatePatch(
						patch.coin(),
						patch.rate(),
						settlementVector.get(patch.coin()),
						patch.timestamp()
		));
		super.subscribeFuturesFundingRates(coinsToSub, handlerWithSettlement);
	}

	@Override
	public void close() {
		super.close();
		fundingSettlementScheduler.cancelNow();
	}
}
