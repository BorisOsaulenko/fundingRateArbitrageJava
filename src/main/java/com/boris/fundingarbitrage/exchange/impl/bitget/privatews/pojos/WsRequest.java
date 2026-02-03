package com.boris.fundingarbitrage.exchange.impl.bitget.privatews.pojos;

public record WsRequest(String op, Arg[] args) {
	public record Arg(String instType, String channel, String instId) {}
}
