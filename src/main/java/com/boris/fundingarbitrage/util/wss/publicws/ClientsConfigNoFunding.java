package com.boris.fundingarbitrage.util.wss.publicws;

import com.boris.fundingarbitrage.exchange.publicws.DomainClientConfig;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;

public record ClientsConfigNoFunding(
				DomainClientConfig<BookTickerPatch> futuresBookTicker,
				DomainClientConfig<MarkPatch> futuresMark,
				DomainClientConfig<BookTickerPatch> spotBookTicker
) {
}
