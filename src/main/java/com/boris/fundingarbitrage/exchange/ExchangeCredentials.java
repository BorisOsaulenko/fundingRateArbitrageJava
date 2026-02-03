package com.boris.fundingarbitrage.exchange;

import java.security.PrivateKey;

public record ExchangeCredentials(String apiKey, PrivateKey privateKey, String apiSecret, String passphrase) {}
