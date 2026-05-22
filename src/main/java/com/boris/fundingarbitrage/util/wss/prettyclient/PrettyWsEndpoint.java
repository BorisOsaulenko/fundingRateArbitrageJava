package com.boris.fundingarbitrage.util.wss.prettyclient;

import jakarta.websocket.*;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
@ClientEndpoint
public class PrettyWsEndpoint {
	private final Consumer<String> messageHandler;
	private final Consumer<Session> onOpenHook; // e.g., set lastSession, complete connecting, reset attempts
	private final Consumer<Session> onClosePreHook; // e.g., clear lastSession, recreate connecting
	private final Consumer<Throwable> onErrorHook;

	public PrettyWsEndpoint(
					Consumer<String> messageHandler,
					Consumer<Session> onOpenHook,
					Consumer<Session> onClosePreHook,
					Consumer<Throwable> onErrorHook
	) {
		this.messageHandler = messageHandler;
		this.onOpenHook = onOpenHook;
		this.onClosePreHook = onClosePreHook;
		this.onErrorHook = onErrorHook;
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
		if (onErrorHook != null) onErrorHook.accept(throwable);
	}
}
