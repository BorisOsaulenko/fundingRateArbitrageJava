package com.boris.fundingarbitrage.util.https;

import com.boris.fundingarbitrage.util.logger.Logger;
import lombok.Getter;
import lombok.NonNull;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class PrettyHttpClient {
	@Getter
	private static final PrettyHttpClient INSTANCE = new ClientFactory().withRetries(5)
					.withRetryInterval(Duration.ofMillis(500))
					.withTimeout(Duration.ofSeconds(10))
					.client();
	private final CloseableHttpAsyncClient client;

	PrettyHttpClient(@NonNull CloseableHttpAsyncClient client) {
		this.client = client;
		this.client.start();
	}

	private static boolean isHttpError(SimpleHttpResponse response) {
		int sc = response.getCode();
		if (sc == -4946) return false; // Binance "No need to change margin type."
		return sc < 200 || sc >= 300;
	}

	private static String safeBody(SimpleHttpResponse response, int maxChars) {
		String body = (response.getBody() == null) ? "" : response.getBodyText();
		if (maxChars == 0 || body.length() <= maxChars) return body;
		return body.substring(0, maxChars) + "...(truncated)";
	}

	private String safeUri(SimpleHttpRequest request) {
		try {
			return request.getUri().toString();
		} catch (URISyntaxException e) {
			return "Invalid URI";
		}
	}

	private void logHttpError(SimpleHttpRequest request, SimpleHttpResponse response) {
		int status = response.getCode();
		String method = request.getMethod();
		String url = safeUri(request);
		String respHeaders = Arrays.toString(response.getHeaders());
		String body = safeBody(response, 0);

		String msg = String.format(
						"HTTP error. %s %s%nStatus: %d%nResponse headers: %s%nBody:%n%s",
						method,
						url,
						status,
						respHeaders,
						body
		);

		Logger.warn(msg);
	}

	private FutureCallback<SimpleHttpResponse> getCallback(
					SimpleHttpRequest req,
					CompletableFuture<SimpleHttpResponse> cf,
					boolean checkCodes
	) {
		return new FutureCallback<SimpleHttpResponse>() {
			@Override
			public void completed(SimpleHttpResponse res) {
				if (checkCodes && isHttpError(res)) {
					logHttpError(req, res);
					cf.completeExceptionally(new RuntimeException(req.getMethod() +
																												" " +
																												safeUri(req) +
																												" failed: HTTP " +
																												res.getCode()));
				} else {
					cf.complete(res);
				}
			}

			@Override
			public void failed(Exception ex) {
				// Transport failure: no status/body exists here.
				Logger.warn("Request failed (transport). " + req.getMethod() + " " + safeUri(req) + " | " + ex);
				cf.completeExceptionally(ex);
			}

			@Override
			public void cancelled() {
				Logger.warn("Request cancelled. " + req.getMethod() + " " + safeUri(req));
				cf.cancel(true);
			}
		};
	}

	public CompletableFuture<SimpleHttpResponse> send(SimpleHttpRequest req) {
		CompletableFuture<SimpleHttpResponse> cf = new CompletableFuture<>();

		client.execute(req, getCallback(req, cf, true));
		return cf;
	}

	public CompletableFuture<SimpleHttpResponse> sendNoCodeCheck(SimpleHttpRequest req) {
		CompletableFuture<SimpleHttpResponse> cf = new CompletableFuture<>();

		client.execute(req, getCallback(req, cf, false));
		return cf;
	}

	public void destroy() {
		try {
			client.close();
		} catch (Exception e) {
			Logger.error("Failed to close HTTP client: " + e.getMessage());
		}
	}
}
