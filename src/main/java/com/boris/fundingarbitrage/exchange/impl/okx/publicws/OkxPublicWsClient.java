package com.boris.fundingarbitrage.exchange.impl.okx.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.okx.publicrest.OkxPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.DomainClientConfigBuilder;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.modifiable.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.ClientsConfigNoFunding;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;

public class OkxPublicWsClient extends FullFundingViaRest {
	public OkxPublicWsClient(ExchangeContext context, OkxPublicHttpClient publicHttp) {
		URI endpoint = URI.create("wss://ws.okx.com:8443/ws/v5/public");
		MessageHandler messageHandler = new MessageHandler(context);
		WsFrames frames = new WsFrames(context);
		ClientsConfigNoFunding config = new ClientsConfigNoFunding(
						new DomainClientConfigBuilder<BookTickerPatch>(endpoint)
										.orchestrator(1, 30)
										.instanceConfig(300, messageHandler::parseFuturesBookTickerMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeFuturesBookTickerFrame,
														frames::getUnsubscribeFuturesBookTickerFrame
										)
										.build(),
						new DomainClientConfigBuilder<MarkPatch>(endpoint)
										.orchestrator(1, 30)
										.instanceConfig(300, messageHandler::parseMarkPriceMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeFuturesMarkPriceFrame,
														frames::getUnsubscribeFuturesMarkPriceFrame
										)
										.build(),
						new DomainClientConfigBuilder<BookTickerPatch>(endpoint)
										.orchestrator(1, 30)
										.instanceConfig(200, messageHandler::parseSpotBookTickerMessageSymbol)
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
