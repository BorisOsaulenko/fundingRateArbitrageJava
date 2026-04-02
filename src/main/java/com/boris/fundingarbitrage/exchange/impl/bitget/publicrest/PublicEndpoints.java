package com.boris.fundingarbitrage.exchange.impl.bitget.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

class PublicEndpoints {
	private static final String baseUrl = "https://api.bitget.com";
	private static final String productType = "USDT-FUTURES";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickersRequest() {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v2/mix/market/tickers")
						.addParameter("productType", productType)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest currentFundingRateRequest() {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v2/mix/market/current-fund-rate")
						.addParameter("productType", productType)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest contractConfigRequest() {
		URI uri = new URIBuilder(baseUrl)
						.setPath("/api/v2/mix/market/contracts")
						.addParameter("productType", productType)
						.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotTickersRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v2/spot/market/tickers").build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotSymbolsRequest() {
		URI uri = new URIBuilder(baseUrl).setPath("/api/v2/spot/public/symbols").build();
		return new SimpleHttpRequest("GET", uri);
	}
}
