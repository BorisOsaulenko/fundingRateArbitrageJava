package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import lombok.NonNull;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public abstract class FundingSettlementViaRest extends PublicWsClient {
	private final CoinVector<Boolean> updatingFundingRateVector = new CoinVector<>();
	private final CoinVector<Instant> settlementVector = new CoinVector<>();
	private final CoinVector<CompletableFuture<FundingRate>> fundingRateFutureVector = new CoinVector<>();

	public FundingSettlementViaRest(PublicWsClient client) {
		super(client);
	}

	public void updateFundingSettlementForCoin(String coin) {
		updatingFundingRateVector.put(coin, true);
		CompletableFuture<FundingRate> future = this.publicHttpClient.getFundingRate(coin).thenApply((fr) -> {
			updatingFundingRateVector.put(coin, false);
			settlementVector.put(coin, fr.settlement());
			return fr;
		});
		fundingRateFutureVector.put(coin, future);
	}

	@Override
	protected void handleFundingRatePatch(@NonNull FundingRatePatch patch) {
		String coin = patch.coin();
		Boolean updating = updatingFundingRateVector.get(coin);
		Instant settlement = settlementVector.get(coin);

		if (updating == null) {
			updateFundingSettlementForCoin(coin);
			return;
		}

		if (updating) return;
		if (settlement == null || Instant.now().isAfter(settlement)) {
			updateFundingSettlementForCoin(coin);
			return;
		}

		FundingRatePatch fundingWithSettlement = new FundingRatePatch(
						patch.coin(),
						patch.rate(),
						settlement,
						patch.timestamp()
		);

		dispatchPatchToHandlers(fundingWithSettlement, fundingRateHandlers);
	}
}
