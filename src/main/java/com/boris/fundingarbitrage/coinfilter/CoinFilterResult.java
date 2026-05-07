package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import lombok.NonNull;

public record CoinFilterResult(
				CoinAvailabilityRecord coinAvailability,
				ConstantDataRecord constantDataRecord,
				ExchangeCoinMap<FuturesSnapshot> initialFuturesSnapshots,
				ExchangeCoinMap<SpotSnapshot> initialSpotSnapshots
) {
	public CoinFilterResult(
					@NonNull CoinAvailabilityRecord coinAvailability,
					@NonNull ConstantDataRecord constantDataRecord,
					@NonNull ExchangeCoinMap<FuturesSnapshot> initialFuturesSnapshots,
					@NonNull ExchangeCoinMap<SpotSnapshot> initialSpotSnapshots
	) {
		this.coinAvailability = coinAvailability;
		this.constantDataRecord = constantDataRecord;
		this.initialFuturesSnapshots = initialFuturesSnapshots;
		this.initialSpotSnapshots = initialSpotSnapshots;
	}


}
