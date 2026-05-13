package com.boris.fundingarbitrage.logic.balanceprovider;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface IBalanceProvider {
	CompletableFuture<Map<BaseExchange, ExchangeBalance>> loadBalances();
}
