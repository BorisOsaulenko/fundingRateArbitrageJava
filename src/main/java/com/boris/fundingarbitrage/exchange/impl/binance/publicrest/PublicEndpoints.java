package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

public class PublicEndpoints {
	private static final String futuresBaseUrl = "https://fapi.binance.com";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest checkSymbolExistsRequest(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/exchangeInfo")
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingRateRequest(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/premiumIndex")
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest bookTickerRequest(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/ticker/bookTicker")
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest lotSizeRequest(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/exchangeInfo")
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingVolume24hRequest(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/ticker/24hr")
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingVolume1hRequest(String symbol) {
		URI url = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/klines")
						.addParameter("symbol", symbol)
						.addParameter("interval", "1h")
						.addParameter("limit", "1")
						.build();
		return new SimpleHttpRequest("GET", url);
	}
}
