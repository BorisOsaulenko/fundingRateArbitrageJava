package com.boris.fundingarbitrage.util.wss.prettyclient;

import com.boris.fundingarbitrage.util.logger.Logger;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import java.util.Arrays;
import java.util.function.Consumer;

@ClientEndpoint
public class PrettyWsEndpoint {
    private final Consumer<String> messageHandler;
    private final Consumer<Session> onOpenHook; // e.g., set lastSession, complete connecting, reset attempts
    private final Consumer<Session> onClosePreHook; // e.g., clear lastSession, recreate connecting

    public PrettyWsEndpoint(
            Consumer<String> messageHandler, Consumer<Session> onOpenHook, Consumer<Session> onClosePreHook) {
        this.messageHandler = messageHandler;
        this.onOpenHook = onOpenHook;
        this.onClosePreHook = onClosePreHook;
    }

    @OnOpen
    public void onOpen(Session session) {
        if (onOpenHook != null) onOpenHook.accept(session);
    }

    @OnMessage
    public void onMessage(String message) {
        messageHandler.accept(message);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        if (onClosePreHook != null) onClosePreHook.accept(session);
    }

    @OnError
    public void onError(Throwable throwable) {
        Logger.getInstance()
                .error(String.format(
                        "WebSocket error: %s\n%s", throwable.getMessage(), Arrays.toString(throwable.getStackTrace())));
    }
}
