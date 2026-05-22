package com.boris.fundingarbitrage.exchange.publicws;

import java.util.Set;

public interface IInstanceWsFrames {
	String getPingFrame();

	String getSubscribeFrame(Set<String> coins);

	String getUnsubscribeFrame(Set<String> coins);
}
