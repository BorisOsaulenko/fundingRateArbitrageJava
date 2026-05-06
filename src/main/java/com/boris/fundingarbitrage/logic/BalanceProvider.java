package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface BalanceProvider {
	CompletableFuture<Map<BaseExchange, ExchangeBalance>> load(Set<BaseExchange> exchanges);
}
