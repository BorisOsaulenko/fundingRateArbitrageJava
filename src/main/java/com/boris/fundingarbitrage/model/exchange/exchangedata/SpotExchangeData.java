package com.boris.fundingarbitrage.model.exchange.exchangedata;

import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;

public record SpotExchangeData(
				SpotConstantData constantData,
				SpotSnapshot snapshot
) implements ExchangeData {
}
