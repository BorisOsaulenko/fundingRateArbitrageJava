package com.boris.fundingarbitrage.logic;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import lombok.NonNull;

public record ExchangePair(@NonNull BaseExchange longEx, @NonNull BaseExchange shortEx) {
}
