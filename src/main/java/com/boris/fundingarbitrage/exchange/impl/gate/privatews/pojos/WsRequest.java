package com.boris.fundingarbitrage.exchange.impl.gate.privatews.pojos;

public record WsRequest(long time, String channel, String event, String[] payload, Auth auth) {
	public record Auth(String method, String KEY, String SIGN, long time) {}
}
