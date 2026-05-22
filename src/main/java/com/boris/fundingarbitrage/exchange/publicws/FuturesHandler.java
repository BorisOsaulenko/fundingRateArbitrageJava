package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import lombok.NonNull;

import java.util.function.Consumer;

public record FuturesHandler(
				@NonNull Consumer<BookTickerPatch> bookTickerHandler,
				@NonNull Consumer<MarkPatch> markPriceHandler,
				@NonNull Consumer<FundingPatch> fundingRateHandler
) {
}