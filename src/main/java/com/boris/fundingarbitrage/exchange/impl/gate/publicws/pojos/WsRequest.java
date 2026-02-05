package com.boris.fundingarbitrage.exchange.impl.gate.publicws.pojos;

public record WsRequest(long time, String channel, String event, String[] payload) {
	public WsRequest(String channel, String event, String[] payload) {
		this(System.currentTimeMillis() / 1000, channel, event, payload);
	}
}
