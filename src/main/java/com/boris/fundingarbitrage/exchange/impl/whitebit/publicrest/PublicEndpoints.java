package com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

public class PublicEndpoints {
	private static final String baseUrl = "https://whitebit.com";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest marketsRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/public/markets").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest futuresRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/public/futures").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderBookRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v4/public/orderbook/" + symbol)
						.addParameter("limit", "1")
						.addParameter("level", "0")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest recentTradesRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
					.setPath("/api/v4/public/trades/" + symbol)
					.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest publicFeeRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/public/fee").build();
		return new SimpleHttpRequest("GET", uri);
	}
}
