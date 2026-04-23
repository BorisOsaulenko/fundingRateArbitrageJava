package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.model.exchange.constantdata.FuturesConstantData;
import com.boris.fundingarbitrage.model.exchange.constantdata.SpotConstantData;
import com.boris.fundingarbitrage.model.exchange.snapshot.FuturesSnapshot;
import com.boris.fundingarbitrage.model.exchange.snapshot.SpotSnapshot;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import lombok.NonNull;

public record CoinFilterResult(
				@NonNull CoinExchangeSupport coinExchangeSupport,
				@NonNull ExchangeCoinMap<FuturesConstantData> futuresConstantData,
				@NonNull ExchangeCoinMap<SpotConstantData> spotConstantData,
				@NonNull ExchangeCoinMap<FuturesSnapshot> initialFuturesSnapshots,
				@NonNull ExchangeCoinMap<SpotSnapshot> initialSpotSnapshots,
				@NonNull ExchangeCoinMap<Boolean> initialPresentOnFutures,
				@NonNull ExchangeCoinMap<Boolean> initialPresentOnSpot
) {
}
