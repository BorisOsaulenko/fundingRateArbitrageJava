package com.boris.fundingarbitrage.exchange.impl.binance.privatews.pojos;

public record PartialFillEventData(
        String e, // Event Type
        long E, // Event Time
        String s, // Symbol
        String l, // Last Filled Quantity
        String L, // Last Filled Price
        String n, // Commission Amount
        String N, // Commission Asset
        long i // Order ID
        ) {
    public PartialFillEventData {
        if (!"executionReport".equals(e)) {
            throw new IllegalArgumentException("Invalid event type: " + e);
        }
    }
}
