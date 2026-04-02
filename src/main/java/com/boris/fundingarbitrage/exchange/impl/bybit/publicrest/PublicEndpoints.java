package com.boris.fundingarbitrage.exchange.impl.bybit.publicrest;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;

class PublicEndpoints {
	private static final String baseUrl = "https://api.bybit.com";
	private static final String linearCategory = "linear";
	private static final String spotCategory = "spot";

	@SneakyThrows
	public static @NonNull SimpleHttpRequest instrumentsInfoRequest(String paginationIndex) {
		return instrumentsInfoRequest(linearCategory, paginationIndex);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest instrumentsInfoRequest(String category, String paginationIndex) {
		URIBuilder builder = new URIBuilder(baseUrl)
						.setPath("/v5/market/instruments-info")
						.addParameter("category", category)
						.addParameter("limit", "1000");
		if (paginationIndex != null) builder.addParameter("cursor", paginationIndex);

		URI uri = builder.build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickersRequest() {
		return tickersRequest(linearCategory);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest tickersRequest(String category) {
		URI uri = new URIBuilder(baseUrl).setPath("/v5/market/tickers").addParameter("category", category).build();
		return new SimpleHttpRequest("GET", uri);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotInstrumentsInfoRequest() {
		return instrumentsInfoRequest(spotCategory, null);
	}

	@SneakyThrows
	public static @NonNull SimpleHttpRequest spotTickersRequest() {
		return tickersRequest(spotCategory);
	}
}
