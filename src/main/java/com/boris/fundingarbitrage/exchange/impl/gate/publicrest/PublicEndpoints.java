package com.boris.fundingarbitrage.exchange.impl.gate.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

class PublicEndpoints {
	private static final String baseUrl = "https://api.gateio.ws";
	private static final String settle = "usdt";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest contractsRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/futures/" + settle + "/contracts").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickersRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/futures/" + settle + "/tickers").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotCurrencyPairsRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/spot/currency_pairs").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotTickersRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/spot/tickers").build();
		return new SimpleHttpRequest("GET", uri);
	}
}
