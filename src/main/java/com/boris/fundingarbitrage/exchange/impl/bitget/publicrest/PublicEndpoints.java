package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

public class PublicEndpoints {
	private static final String baseUrl = "https://api.bitget.com";
	private static final String productType = "USDT-FUTURES";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest contractsRequest() {
		URI uri = new URIBuilder(baseUrl)
				.setPath("/api/v2/mix/market/contracts")
				.addParameter("productType", productType)
				.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest contractsRequest(String symbol) {
		URI uri = new URIBuilder(baseUrl)
				.setPath("/api/v2/mix/market/contracts")
				.addParameter("productType", productType)
				.addParameter("symbol", symbol)
				.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickerRequest(String symbol) {
		URI uri = new URIBuilder(baseUrl)
				.setPath("/api/v2/mix/market/ticker")
				.addParameter("productType", productType)
				.addParameter("symbol", symbol)
				.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingRateRequest(String symbol) {
		URI uri = new URIBuilder(baseUrl)
				.setPath("/api/v2/mix/market/current-fund-rate")
				.addParameter("productType", productType)
				.addParameter("symbol", symbol)
				.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest candles1hRequest(String symbol) {
		URI uri = new URIBuilder(baseUrl)
				.setPath("/api/v2/mix/market/candles")
				.addParameter("productType", productType)
				.addParameter("symbol", symbol)
				.addParameter("granularity", "1H")
				.addParameter("limit", "1")
				.build();
		return new SimpleHttpRequest("GET", uri);
	}
}
