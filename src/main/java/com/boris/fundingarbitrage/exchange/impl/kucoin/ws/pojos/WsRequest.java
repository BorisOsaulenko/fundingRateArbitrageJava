package com.boris.fundingarbitrage.exchange.impl.kucoin.ws.pojos;

public record WsRequest(
			String id,
			String type,
			String topic,
			boolean privateChannel,
			boolean response
) {}
