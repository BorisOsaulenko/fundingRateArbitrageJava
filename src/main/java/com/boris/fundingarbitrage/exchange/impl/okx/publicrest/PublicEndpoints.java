package com.boris.fundingarbitrage.exchange.impl.okx.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

class PublicEndpoints {
	private static final String baseUrl = "https://www.okx.com";
	private static final String instType = "SWAP";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest instrumentsRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v5/public/instruments")
						.addParameter("instType", instType)
						.addParameter("instId", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest instrumentsRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v5/public/instruments").addParameter("instType", instType).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickerRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v5/market/ticker").addParameter("instId", symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingRateRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v5/public/funding-rate").addParameter("instId", symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingRateRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v5/public/funding-rate").addParameter("instId", "ANY").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest markPriceRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v5/public/mark-price")
						.addParameter("instType", instType)
						.addParameter("instId", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest candles1hRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v5/market/candles")
						.addParameter("instId", symbol)
						.addParameter("bar", "1H")
						.addParameter("limit", "1")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingGranularityRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v5/public/funding-rate").addParameter("instId", "ANY").build();
		return new SimpleHttpRequest("GET", uri);
	}
}
