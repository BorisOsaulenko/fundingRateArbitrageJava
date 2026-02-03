package com.boris.fundingarbitrage.util.https;

import java.net.InetAddress;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class ClientFactory {
    private Duration timeout;
    private int retriesAmount = 5;
    private Duration retryInterval = Duration.ofMillis(300);
    private InetAddress ipAddress;

    public ClientFactory() {
        timeout = Duration.of(10, ChronoUnit.SECONDS); // 10s
    }

    public ClientFactory withTimeout(int timeout) {
        this.timeout = Duration.of(timeout, ChronoUnit.SECONDS);
        return this;
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

    public ClientFactory withIpAddress(String ip) {
        final InetAddress candidate;
        try {
            candidate = InetAddress.getByName(ip);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip, e);
        }

        this.ipAddress = candidate;
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

        final AsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build();

        final HttpRoutePlanner routePlanner =
                (this.ipAddress != null) ? RoutePlannerHelper.getForIp(this.ipAddress) : null;

        final HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
                .setRetryStrategy(retryStrategy)
                .setDefaultRequestConfig(requestConfig)
                .setRoutePlanner(routePlanner)
                .setConnectionManager(connectionManager);

        return new PrettyHttpClient(builder.build());
    }
}
