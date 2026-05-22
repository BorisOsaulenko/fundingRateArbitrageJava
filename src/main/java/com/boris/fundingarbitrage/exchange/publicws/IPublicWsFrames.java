package com.boris.fundingarbitrage.exchange.publicws;

import java.util.Set;

public interface IPublicWsFrames {
	String getPingFrame();

	String getSubscribeFuturesFundingRateFrame(Set<String> coins);

	String getUnsubscribeFuturesFundingRateFrame(Set<String> coins);

	String getSubscribeFuturesBookTickerFrame(Set<String> coins);

	String getUnsubscribeFuturesBookTickerFrame(Set<String> coins);

	String getSubscribeFuturesMarkPriceFrame(Set<String> coins);

	String getUnsubscribeFuturesMarkPriceFrame(Set<String> coins);

	String getSubscribeSpotBookTickerFrame(Set<String> coins);

	String getUnsubscribeSpotBookTickerFrame(Set<String> coins);
}
