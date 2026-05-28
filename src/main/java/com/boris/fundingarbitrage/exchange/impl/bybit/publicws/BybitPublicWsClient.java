package com.boris.fundingarbitrage.exchange.impl.bybit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.bybit.publicrest.BybitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.DomainClientConfigBuilder;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.modifiable.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.ClientsConfigNoFunding;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;

public class BybitPublicWsClient extends FullFundingViaRest {
	public BybitPublicWsClient(ExchangeContext context, BybitPublicHttpClient publicHttp) {
		URI futuresEndpoint = URI.create("wss://stream.bybit.com/v5/public/linear");
		URI spotEndpoint = URI.create("wss://stream.bybit.com/v5/public/spot");
		MessageHandler messageHandler = new MessageHandler(context);
		WsFrames frames = new WsFrames(context);
		ClientsConfigNoFunding config = new ClientsConfigNoFunding(
						new DomainClientConfigBuilder<BookTickerPatch>(futuresEndpoint)
										.orchestrator(5, 20)
										.instanceConfig(50, messageHandler::parseFuturesBookTickerMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeFuturesBookTickerFrame,
														frames::getUnsubscribeFuturesBookTickerFrame
										)
										.build(),
						new DomainClientConfigBuilder<MarkPatch>(futuresEndpoint)
										.orchestrator(5, 20)
										.instanceConfig(50, messageHandler::parseMarkPriceMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeFuturesMarkPriceFrame,
														frames::getUnsubscribeFuturesMarkPriceFrame
										)
										.build(),
						new DomainClientConfigBuilder<BookTickerPatch>(spotEndpoint)
										.orchestrator(20, 20)
										.instanceConfig(10, messageHandler::parseSpotBookTickerMessageSymbol)
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
