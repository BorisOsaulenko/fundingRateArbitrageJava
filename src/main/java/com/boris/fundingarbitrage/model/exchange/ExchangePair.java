package com.boris.fundingarbitrage.model.exchange;

import lombok.NonNull;

public record ExchangePair(
        @NonNull ExchangeName longExchange,
        @NonNull ExchangeName shortExchange,
        @NonNull String coin) {}
