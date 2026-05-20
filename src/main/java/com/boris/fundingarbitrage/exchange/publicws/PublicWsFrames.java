package com.boris.fundingarbitrage.exchange.publicws;

import java.util.Set;

public interface PublicWsFrames {
	String getSpotPingFrame();

	String getFuturesPingFrame();

	String getSubscribeFuturesFundingRateFrame(Set<String> symbols);

	String getUnsubscribeFuturesFundingRateFrame(Set<String> symbols);

	String getSubscribeFuturesBookTickerFrame(Set<String> symbols);

	String getUnsubscribeFuturesBookTickerFrame(Set<String> symbols);

	String getSubscribeFuturesMarkPriceFrame(Set<String> symbols);

	String getUnsubscribeFuturesMarkPriceFrame(Set<String> symbols);

	String getSubscribeSpotBookTickerFrame(Set<String> symbols);

	String getUnsubscribeSpotBookTickerFrame(Set<String> symbols);
}
