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
	public static @NonNull SimpleHttpRequest contractRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/futures/" + settle + "/contracts/" + symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest contractsRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/futures/" + settle + "/contracts").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickersRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v4/futures/" + settle + "/tickers")
						.addParameter("contract", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest candlesticks1hRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v4/futures/" + settle + "/candlesticks")
						.addParameter("contract", symbol)
						.addParameter("interval", "1h")
						.addParameter("limit", "1")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest orderBookRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v4/futures/" + settle + "/order_book")
						.addParameter("contract", symbol)
						.addParameter("limit", "1")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingGranularityRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v4/futures/" + settle + "/contracts").build();
		return new SimpleHttpRequest("GET", uri);
	}
}
