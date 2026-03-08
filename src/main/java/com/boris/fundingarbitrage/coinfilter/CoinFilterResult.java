package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public record CoinFilterResult(
				CoinVector<Set<BaseExchange>> availableExchangesByCoin,
				Map<BaseExchange, Set<String>> availableCoinsByExchange,
				ExchangeCoinMap<BigDecimal> lotSizes,
				ExchangeCoinMap<Integer> fundingIntervals
) {
}
