package com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos;

public record MarkPriceMessage(
        String e, // Event type
        long E, // Event time
        String s, // Symbol
        String p // Mark price
        ) {}
