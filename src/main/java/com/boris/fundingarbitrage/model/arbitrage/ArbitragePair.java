package com.boris.fundingarbitrage.model.arbitrage;

import com.boris.fundingarbitrage.model.exchange.ExchangePair;
import lombok.NonNull;

public record ArbitragePair(
        @NonNull ArbitrageSnapshot snapshot, @NonNull ExchangePair pair) {}
