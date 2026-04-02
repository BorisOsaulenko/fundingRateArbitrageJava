package com.boris.fundingarbitrage.util.https;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class ClientFactory {
	private final boolean isHttp1Forced = false;
	private Duration timeout;
	private int retriesAmount = 5;
	private Duration retryInterval = Duration.ofMillis(300);

	public ClientFactory() {
		timeout = Duration.of(10, ChronoUnit.SECONDS); // 10s
	}

	public ClientFactory withRetries(int retries) {
		this.retriesAmount = retries;
		return this;
	}

	public ClientFactory withRetryInterval(Duration retryInterval) {
		this.retryInterval = retryInterval;
		return this;
	}

	public ClientFactory withTimeout(Duration timeout) {
		this.timeout = Objects.requireNonNull(timeout, "timeout");
		return this;
	}

	public PrettyHttpClient client() {
		final HttpRequestRetryStrategy retryStrategy =
						new DefaultHttpRequestRetryStrategy(this.retriesAmount, TimeValue.of((this.retryInterval)));
		final RequestConfig requestConfig = RequestConfig.custom()
						.setResponseTimeout(Timeout.of(this.timeout))
						.setConnectionRequestTimeout(Timeout.of(this.timeout))
						.build();
		final ConnectionConfig connectionConfig = ConnectionConfig.custom()
						.setConnectTimeout(Timeout.of(this.timeout))
						.build();

		final var connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
						.setDefaultConnectionConfig(connectionConfig);

		if (this.isHttp1Forced) {
			final TlsConfig tlsConfig = TlsConfig.custom().setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1).build();
			connectionManager.setTlsConfigResolver(_ -> tlsConfig);
		}

		final HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
						.setRetryStrategy(retryStrategy)
						.setDefaultRequestConfig(requestConfig)
						.setConnectionManager(connectionManager.build());

		return new PrettyHttpClient(builder.build());
	}
}
