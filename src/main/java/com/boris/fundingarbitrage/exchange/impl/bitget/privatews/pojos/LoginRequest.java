package com.boris.fundingarbitrage.exchange.impl.bitget.privatews.pojos;

public record LoginRequest(String op, LoginArg[] args) {
	public record LoginArg(String apiKey, String passphrase, String timestamp, String sign) {}
}
