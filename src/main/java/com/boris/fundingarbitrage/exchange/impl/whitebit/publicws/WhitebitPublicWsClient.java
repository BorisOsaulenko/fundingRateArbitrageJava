package com.boris.fundingarbitrage.exchange.impl.whitebit.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.whitebit.publicrest.WhitebitPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.DomainClientConfigBuilder;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.ClientsConfigNoFunding;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;

public class WhitebitPublicWsClient extends FullFundingViaRest {
	public WhitebitPublicWsClient(ExchangeContext context, WhitebitPublicHttpClient publicHttp) {
		URI endpoint = URI.create("wss://api.whitebit.com/ws");
		MessageHandler messageHandler = new MessageHandler(context);
		WsFrames frames = new WsFrames(context);
		ClientsConfigNoFunding config = new ClientsConfigNoFunding(
						new DomainClientConfigBuilder<BookTickerPatch>(endpoint)
										.orchestrator(10, 50)
										.instanceConfig(1, messageHandler::parseFuturesBookTickerMessageSymbol)
										.frames(frames::getPingFrame, frames::getSubscribeFuturesBookTickerFrame, _ -> null)
										.build(),
						new DomainClientConfigBuilder<MarkPatch>(endpoint)
										.orchestrator(10, 50)
										.instanceConfig(1, messageHandler::parseMarkPriceMessageSymbol)
										.frames(frames::getPingFrame, frames::getSubscribeFuturesMarkPriceFrame, _ -> null)
										.build(),
						new DomainClientConfigBuilder<BookTickerPatch>(endpoint)
										.orchestrator(10, 50)
										.instanceConfig(1, messageHandler::parseSpotBookTickerMessageSymbol)
										.frames(frames::getPingFrame, frames::getSubscribeSpotBookTickerFrame, _ -> null)
										.build()
		);
		super(config, publicHttp, new ProdModifiableSchedulerBuilder());
	}
}
