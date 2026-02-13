package com.boris.fundingarbitrage.exchange.impl.binance.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

public class PublicEndpoints {
	private static final String futuresBaseUrl = "https://fapi.binance.com";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest checkSymbolExistsRequestSymbol(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/exchangeInfo").addParameter("symbol", symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest checkSymbolExistsRequestSymbols() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/exchangeInfo").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingRateRequestSymbol(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/premiumIndex").addParameter("symbol", symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest fundingRateRequestSymbols() {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/premiumIndex").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest bookTickerRequestSymbol(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/ticker/bookTicker")
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest lotSizeRequestSymbol(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/exchangeInfo").addParameter("symbol", symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingVolume24hRequestSymbol(String symbol) {
		URI uri = new URIBuilder(futuresBaseUrl).setPath("/fapi/v1/ticker/24hr").addParameter("symbol", symbol).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tradingVolume1hRequestSymbol(String symbol) {
		URI url = new URIBuilder(futuresBaseUrl)
						.setPath("/fapi/v1/klines")
						.addParameter("symbol", symbol)
						.addParameter("interval", "1h")
						.addParameter("limit", "1")
						.build();
		return new SimpleHttpRequest("GET", url);
	}
}
