package com.boris.fundingarbitrage.logic.balancespolicy;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.model.exchange.ExchangeBalance;

import java.util.Map;

public interface IBalancesPolicy {
	void validateBalancesMap(Map<BaseExchange, ExchangeBalance> balances);
}
