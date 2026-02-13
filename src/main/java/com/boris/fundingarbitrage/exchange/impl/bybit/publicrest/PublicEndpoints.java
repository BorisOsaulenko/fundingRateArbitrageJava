package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

public class PublicEndpoints {
	private static final String baseUrl = "https://api.bybit.com";
	private static final String category = "linear";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest instrumentsInfoRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/v5/market/instruments-info")
						.addParameter("category", category)
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest instrumentsInfoRequestSymbols() {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/v5/market/instruments-info")
						.addParameter("category", category)
						.addParameter("limit", "1000")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickersRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/v5/market/tickers")
						.addParameter("category", category)
						.addParameter("symbol", symbol)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickersRequestSymbols() {
		URI uri = new URIBuilder(baseUrl).setPath("/v5/market/tickers").addParameter("category", category).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest kline1hRequestSymbol(String symbol) {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/v5/market/kline")
						.addParameter("category", category)
						.addParameter("symbol", symbol)
						.addParameter("interval", "60")
						.addParameter("limit", "1")
						.build();
		return new SimpleHttpRequest("GET", uri);
	}
}
