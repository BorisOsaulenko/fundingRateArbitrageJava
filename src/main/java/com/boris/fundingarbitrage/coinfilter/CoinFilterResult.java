package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.monitor.ExchangeCoinMap;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public record CoinFilterResult(
				CoinVector<Set<BaseExchange>> availableExchangesByCoin,
				Map<BaseExchange, Set<String>> availableCoinsByExchange,
				ExchangeCoinMap<BigDecimal> lotSizesMap,
				ExchangeCoinMap<Integer> fundingIntervalsMap,
				ExchangeCoinMap<BookTicker> bookTickersMap,
				ExchangeCoinMap<FundingRate> fundingRatesMap
) {
}
