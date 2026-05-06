package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;

import java.util.Map;
import java.util.Set;

public record GlobalState(
				Map<BaseExchange, Set<String>> presentOnSpot,
				Map<BaseExchange, Set<String>> presentOnFutures
) {
}
