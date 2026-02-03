package com.boris.fundingarbitrage.exchange.impl.bitget.publicws.pojos;

public record WsRequest(String op, Arg[] args) {
	public record Arg(String instType, String channel, String instId) {}
}
