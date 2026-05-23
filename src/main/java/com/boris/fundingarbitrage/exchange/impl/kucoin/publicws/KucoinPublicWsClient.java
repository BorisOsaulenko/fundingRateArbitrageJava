package com.boris.fundingarbitrage.exchange.impl.kucoin.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.kucoin.publicrest.KucoinPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.DomainClientConfigBuilder;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.ClientsConfigNoFunding;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class KucoinPublicWsClient extends FullFundingViaRest {
	public KucoinPublicWsClient(ExchangeContext context, KucoinPublicHttpClient publicHttp) {
		MessageHandler messageHandler = new MessageHandler(context);
		CompletableFuture<URI> spotEndpointFuture = publicHttp.fetchPublicSpotToken();
		CompletableFuture<URI> futuresEndpointFuture = publicHttp.fetchPublicFuturesToken();
		WsFrames frames = new WsFrames(context);

		ClientsConfigNoFunding config = new ClientsConfigNoFunding(
						new DomainClientConfigBuilder<BookTickerPatch>(futuresEndpointFuture)
										.orchestrator(5, 18)
										.instanceConfig(90, messageHandler::parseFuturesBookTickerMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeFuturesBookTickerFrame,
														frames::getUnsubscribeFuturesBookTickerFrame
										)
										.build(),
						new DomainClientConfigBuilder<MarkPatch>(futuresEndpointFuture)
										.orchestrator(5, 18)
										.instanceConfig(90, messageHandler::parseMarkPriceMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeFuturesMarkPriceFrame,
														frames::getUnsubscribeFuturesMarkPriceFrame
										)
										.build(),
						new DomainClientConfigBuilder<BookTickerPatch>(spotEndpointFuture)
										.orchestrator(5, 18)
										.instanceConfig(90, messageHandler::parseSpotBookTickerMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeSpotBookTickerFrame,
														frames::getUnsubscribeSpotBookTickerFrame
										)
										.build()
		);

		super(config, publicHttp, new ProdModifiableSchedulerBuilder());
	}
}
