package com.boris.fundingarbitrage.model.exchange.exchangedata;

import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;

public record FuturesExchangeData(
				FuturesConstantData constantData,
				FuturesSnapshot snapshot
) implements ExchangeData {
}
