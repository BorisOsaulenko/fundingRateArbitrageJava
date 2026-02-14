package com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

class PublicEndpoints {
	private static final String baseUrl = "https://api-futures.kucoin.com";

	private PublicEndpoints() {}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest contractDetailRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/contracts/" + symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest activeContractsRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/contracts/active").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickerRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/ticker").addParameter("symbol", symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingRateRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/funding-rate/" + symbol + "/current").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest klines1hRequestSymbol(String symbol) {
		long now = System.currentTimeMillis();
		long oneHourAgo = now - 3_600_000L;
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v1/kline/query")
						.addParameter("symbol", symbol)
						.addParameter("granularity", "60")
						.addParameter("from", String.valueOf(oneHourAgo))
						.addParameter("to", String.valueOf(now))
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest publicWsToken() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/bullet-public").build();
		return new SimpleHttpRequest("POST", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingGranularityRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v1/contracts/active").build();
		return new SimpleHttpRequest("GET", uri);
	}
}
