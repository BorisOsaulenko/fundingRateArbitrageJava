package com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos;

public record FundingRateMessage(
        String e, // Event type
        long E, // Event time
        String s, // Symbol
        String r, // Funding rate,
        long T // Next funding time
        ) {}
