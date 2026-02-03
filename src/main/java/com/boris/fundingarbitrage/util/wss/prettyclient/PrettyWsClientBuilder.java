package com.boris.fundingarbitrage.util.wss.prettyclient;

import jakarta.websocket.Session;
import java.net.URI;
import java.util.function.Consumer;

public class PrettyWsClientBuilder {
    private final URI endpoint;
    private final Consumer<String> messageProcessor;
    private Consumer<Session> onOpenHook = null;
    private Consumer<Session> onCloseHook = null;

    public PrettyWsClientBuilder(URI endpoint, Consumer<String> messageProcessor) {
        this.endpoint = endpoint;
        this.messageProcessor = messageProcessor;
    }

    public PrettyWsClientBuilder withOnOpenHook(Consumer<Session> onOpenHook) {
        this.onOpenHook = onOpenHook;
        return this;
    }

    public PrettyWsClientBuilder withOnCloseHook(Consumer<Session> onCloseHook) {
        this.onCloseHook = onCloseHook;
        return this;
    }

    public PrettyWsClient build() {
        return new PrettyWsClient(endpoint, messageProcessor, onOpenHook, onCloseHook);
    }
}
