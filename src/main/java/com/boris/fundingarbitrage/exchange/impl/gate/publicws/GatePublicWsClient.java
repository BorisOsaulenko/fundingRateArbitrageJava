package com.boris.fundingarbitrage.exchange.impl.gate.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.gate.publicrest.GatePublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.DomainClientConfigBuilder;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;
import com.boris.fundingarbitrage.util.wss.publicws.FundingSettlementViaRest;

import java.net.URI;

public class GatePublicWsClient extends FundingSettlementViaRest {
	public GatePublicWsClient(ExchangeContext context, GatePublicHttpClient publicHttp) {
		URI futuresEndpoint = URI.create("wss://fx-ws.gateio.ws/v4/ws/usdt");
		URI spotEndpoint = URI.create("wss://api.gateio.ws/ws/v4/");
		WsFrames frames = new WsFrames(context);
		MessageHandler messageHandler = new MessageHandler(context);
		ClientsConfig config = new ClientsConfig(
						new DomainClientConfigBuilder<BookTickerPatch>(futuresEndpoint)
										.orchestrator(5, 50)
										.instanceConfig(50, messageHandler::parseFuturesBookTickerMessageSymbol)
										.frames(
														frames::getFuturesPingFrame,
														frames::getSubscribeFuturesBookTickerFrame,
														frames::getUnsubscribeFuturesBookTickerFrame
										)
										.build(),
						new DomainClientConfigBuilder<FundingPatch>(futuresEndpoint)
										.orchestrator(5, 50)
										.instanceConfig(50, messageHandler::parseFundingRateMessageSymbol)
										.frames(
														frames::getFuturesPingFrame,
														frames::getSubscribeFuturesFundingRateFrame,
														frames::getUnsubscribeFuturesFundingRateFrame
										)
										.build(),
						new DomainClientConfigBuilder<MarkPatch>(futuresEndpoint)
										.orchestrator(5, 50)
										.instanceConfig(50, messageHandler::parseMarkPriceMessageSymbol)
										.frames(
														frames::getFuturesPingFrame,
														frames::getSubscribeFuturesMarkPriceFrame,
														frames::getUnsubscribeFuturesMarkPriceFrame
										)
										.build(),
						new DomainClientConfigBuilder<BookTickerPatch>(spotEndpoint)
										.orchestrator(5, 50)
										.instanceConfig(50, messageHandler::parseSpotBookTickerMessageSymbol)
										.frames(
														frames::getSpotPingFrame,
														frames::getSubscribeSpotBookTickerFrame,
														frames::getUnsubscribeSpotBookTickerFrame
										)
										.build()
		);
		super(config, publicHttp, new ProdModifiableSchedulerBuilder());
	}
}
