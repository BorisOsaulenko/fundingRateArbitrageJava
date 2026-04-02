package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

class PublicEndpoints {
	private static final String baseUrl = "https://api-futures.kucoin.com";
	private static final String spotBaseUrl = "https://api.kucoin.com";

	private PublicEndpoints() {}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest activeContractsRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/contracts/active").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest allTickersRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/allTickers").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest publicWsToken() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/bullet-public").build();
		return new SimpleHttpRequest("POST", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotSymbolsRequest() {
		URI uri = new URIBuilder(spotBaseUrl).setPath("/api/v2/symbols").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotAllTickersRequest() {
		URI uri = new URIBuilder(spotBaseUrl).setPath("/api/v1/market/allTickers").build();
		return new SimpleHttpRequest("GET", uri);
	}
}
