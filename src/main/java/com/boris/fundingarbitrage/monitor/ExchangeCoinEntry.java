package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.model.exchange.ExchangeName;

public record ExchangeCoinEntry<T>(ExchangeName name, String coin, T value) {}
