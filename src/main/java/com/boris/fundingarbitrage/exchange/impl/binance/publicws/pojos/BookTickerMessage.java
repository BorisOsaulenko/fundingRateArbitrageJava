package com.boris.fundingarbitrage.exchange.impl.binance.publicws.pojos;

public record BookTickerMessage(
        String e, // Event type
        long E, // Event time
        String s, // Symbol
        String b, // Best bid price
        String B, // Best bid qty
        String a, // Best ask price
        String A // Best ask qty
        ) {}
