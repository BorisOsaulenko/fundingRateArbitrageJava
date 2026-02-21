package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;

public record ExchangeCoinEntry<T>(BaseExchange exchange, String coin, T value) {}
