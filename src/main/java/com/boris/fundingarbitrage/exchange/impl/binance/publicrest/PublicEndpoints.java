package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

class PublicEndpoints {
	private static final String futuresBaseUrl = "https://fapi.binance.com";
	private static final String spotBaseUrl = "https://api.binance.com";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest premiumIndexRequest() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/premiumIndex").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest bookTickerRequest() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/ticker/bookTicker").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest exchangeInfoRequest() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/exchangeInfo").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest statistic24hRequest() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/ticker/24hr").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingInfoRequest() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/fundingInfo").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotBookTickerRequest() {
		URI uri = new URIBuilder(spotBaseUrl).setPath("/api/v3/ticker/bookTicker").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotExchangeInfoRequest() {
		URI uri = new URIBuilder(spotBaseUrl).setPath("/api/v3/exchangeInfo").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotStatistic24hRequest() {
		URI uri = new URIBuilder(spotBaseUrl).setPath("/api/v3/ticker/24hr").build();
		return new SimpleHttpRequest("GET", uri);
	}
}
