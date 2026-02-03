package com.boris.fundingarbitrage.exchange.impl.binance.privatews.pojos;

public record DepositEventData(
        String e, // Event Type
        long E, // Event Time
        BalanceEntry[] B // Balances
        ) {
    public DepositEventData {
        if (!"outboundAccountPosition".equals(e)) {
            throw new IllegalArgumentException("Invalid event type: " + e);
        }
    }
}
