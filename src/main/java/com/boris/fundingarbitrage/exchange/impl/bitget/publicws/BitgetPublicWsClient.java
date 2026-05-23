package com.boris.fundingarbitrage.exchange.impl.bitget.publicws;

import com.boris.fundingarbitrage.exchange.impl.bitget.BitgetContext;
import com.boris.fundingarbitrage.exchange.impl.bitget.publicrest.BitgetPublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.DomainClientConfigBuilder;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.ClientsConfigNoFunding;
import com.boris.fundingarbitrage.util.wss.publicws.FullFundingViaRest;

import java.net.URI;

public class BitgetPublicWsClient extends FullFundingViaRest {
	public BitgetPublicWsClient(BitgetContext context, BitgetPublicHttpClient publicHttp) {
		MessageHandler messageHandler = new MessageHandler(context);
		WsFrames frames = new WsFrames(context);
		URI endpoint = URI.create("wss://ws.bitget.com/v2/ws/public");
		ClientsConfigNoFunding config = new ClientsConfigNoFunding(
						new DomainClientConfigBuilder<BookTickerPatch>(endpoint)
										.orchestrator(5, 30)
										.instanceConfig(50, messageHandler::parseFuturesBookTickerMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeFuturesBookTickerFrame,
														frames::getUnsubscribeFuturesBookTickerFrame
										)
										.build(),
						new DomainClientConfigBuilder<MarkPatch>(endpoint)
										.orchestrator(5, 30)
										.instanceConfig(50, messageHandler::parseMarkPriceMessageSymbol)
										.frames(
														frames::getPingFrame,
														frames::getSubscribeFuturesMarkPriceFrame,
														frames::getUnsubscribeFuturesMarkPriceFrame
										)
										.build(),
						new DomainClientConfigBuilder<BookTickerPatch>(endpoint)
										.orchestrator(5, 30)
										.instanceConfig(50, messageHandler::parseSpotBookTickerMessageSymbol)
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
