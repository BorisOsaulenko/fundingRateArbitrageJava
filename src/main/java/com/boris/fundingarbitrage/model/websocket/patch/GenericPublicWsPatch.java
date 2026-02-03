package com.boris.fundingarbitrage.model.websocket.patch;

import java.time.Instant;

public interface GenericPublicWsPatch {
	String coin();

	Instant timestamp();
}
