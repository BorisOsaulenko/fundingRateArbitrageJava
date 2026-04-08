package com.boris.fundingarbitrage.model.exchange.exchangedata;

import com.boris.fundingarbitrage.model.exchange.constantdata.ConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;

public sealed interface ExchangeData permits FuturesExchangeData, SpotExchangeData {
	Snapshot snapshot();

	ConstantData constantData();

	default boolean equalsRefs(ExchangeData other) {
		return this.constantData() == other.constantData() && this.snapshot() == other.snapshot();
	}
}
