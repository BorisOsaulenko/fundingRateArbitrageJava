package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.scheduler.IModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.NonNull;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public abstract class PublicWsFundingSettlementViaRest extends PublicWsClient {
	private final CoinVector<Instant> settlementVector = new CoinVector<>();
	private boolean updatingSettlements = false;
	private boolean updateScheduled = false;
	private CompletableFuture<Void> updatingFuture = CompletableFuture.completedFuture(null);

	public PublicWsFundingSettlementViaRest(
					ExchangeContext context,
					URI endpoint,
					PublicMessageHandler messageHandler,
					PublicHttpClient publicHttp,
					IModifiableSchedulerBuilder schedulerBuilder
	) {
		super(context, endpoint, messageHandler, publicHttp, schedulerBuilder);
	}

	private void updateFundingSettlementForCoins() {
		if (updatingSettlements) return;

		updatingSettlements = true;
		updatingFuture = this.publicHttpClient.getFundingRate(futuresFundingRateHandlers.keySet()).thenAccept((rates) -> {
			updatingSettlements = false;
			rates.forEach((coin, rate) -> {
				if (rate == null) return;
				settlementVector.put(coin, rate.settlement());
			});
		});
	}

	private void scheduleSettlementUpdate() {
		if (updateScheduled) return;
		updateScheduled = true;
		updatingFuture.whenComplete((_, _) -> {
			updateScheduled = false;
			updateFundingSettlementForCoins();
		});
	}

	@Override
	protected void handleFuturesFundingRatePatch(@NonNull FundingRatePatch patch) {
		if (updatingSettlements) return;

		String coin = patch.coin();
		Instant settlement = settlementVector.get(coin);

		if (settlement == null || Instant.now().isAfter(settlement)) {
			scheduleSettlementUpdate();
			return;
		}

		FundingRatePatch fundingWithSettlement = new FundingRatePatch(
						patch.coin(),
						patch.rate(),
						settlement,
						patch.timestamp()
		);

		dispatchPatchToHandlers(fundingWithSettlement, futuresFundingRateHandlers);
	}
}
