package com.boris.fundingarbitrage.exchange.impl.kucoin.privatews;

record PongResponse(String id, String type) {
	public PongResponse(String id) {
		this(id, "pong");
	}
}
