package com.boris.fundingarbitrage.util.wss.publicmessagehandler;

import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class FundingSettlementViaRest<T extends PublicMessageHandler> extends PublicMessageHandler {
	private final CoinVector<Boolean> updatingFundingRateVector = new CoinVector<>();
	private final CoinVector<Instant> settlementVector = new CoinVector<>();
	private final CoinVector<CompletableFuture<FundingRate>> fundingRateFutureVector = new CoinVector<>();

	private final T handler;

	public FundingSettlementViaRest(T handler) {
		super(handler.publicHttpClient);
		this.handler = handler;
	}

	@Override
	public BookTickerPatch parseBookTickerMessageSymbol(String message) {
		return handler.parseBookTickerMessageSymbol(message);
	}

	@Override
	public MarkPricePatch parseMarkPriceMessageSymbol(String message) {
		return handler.parseMarkPriceMessageSymbol(message);
	}

	@Override
	public String getResponseToPingMessage(String message) {
		return handler.getResponseToPingMessage(message);
	}

	public void updateFundingSettlementForCoin(String coin) {
		updatingFundingRateVector.put(coin, true);
		CompletableFuture<FundingRate> future = this.publicHttpClient
						.getFundingRate(coin)
						.thenApply((fr) -> {
							updatingFundingRateVector.put(coin, false);
							settlementVector.put(coin, fr.settlement());
							return fr;
						});
		fundingRateFutureVector.put(coin, future);
	}

	@Override
	public FundingRatePatch parseFundingRateMessageSymbol(String message) {
		FundingRatePatch fundingRate = this.handler.parseFundingRateMessageSymbol(message);
		if (fundingRate == null) return null;

		String coin = fundingRate.coin();

		Boolean updating = updatingFundingRateVector.get(coin);
		if (updating == null) {
			updateFundingSettlementForCoin(coin);
			return null;
		}

		Instant settlement = settlementVector.get(coin);
		if (settlement == null || Instant.now().isAfter(settlement)) {
			updateFundingSettlementForCoin(coin);
			return null;
		}

		return new FundingRatePatch(
						fundingRate.coin(),
						fundingRate.rate(),
						settlement,
						fundingRate.timestamp()
		);
	}
}
