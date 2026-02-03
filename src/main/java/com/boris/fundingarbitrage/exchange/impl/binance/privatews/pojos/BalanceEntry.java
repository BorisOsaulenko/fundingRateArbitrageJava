package com.boris.fundingarbitrage.exchange.impl.binance.privatews.pojos;

public record BalanceEntry(
        String a, // Asset
        String f, // Free
        String l // Locked
        ) {}
