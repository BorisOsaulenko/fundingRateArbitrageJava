package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.DomainClientConfigBuilder;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.modifiable.ProdModifiableSchedulerBuilder;

import java.net.URI;

public class BinancePublicWsClient extends PublicWsClient {
	public BinancePublicWsClient(ExchangeContext context) {
		URI futuresFundingAndMarkEndpoint = URI.create("wss://fstream.binance.com/market/ws");
		URI futuresBookTickerEndpoint = URI.create("wss://fstream.binance.com/public/ws");
		URI spotEndpoint = URI.create("wss://stream.binance.com:9443/ws");
		WsFrames frames = new WsFrames(context);
		MessageHandler messageHandler = new MessageHandler(context);
		ClientsConfig config = new ClientsConfig(
						new DomainClientConfigBuilder<BookTickerPatch>(futuresBookTickerEndpoint)
										.orchestrator(5, 0)
										.instanceConfig(100, messageHandler::parseFuturesBookTickerMessageSymbol)
										.frames(frames::getSubscribeFuturesBookTickerFrame, frames::getUnsubscribeFuturesBookTickerFrame)
										.build(),
						new DomainClientConfigBuilder<FundingPatch>(futuresFundingAndMarkEndpoint)
										.orchestrator(2, 0)
										.instanceConfig(100, messageHandler::parseFundingRateMessageSymbol)
										.frames(frames::getSubscribeFuturesFundingRateFrame, frames::getUnsubscribeFuturesFundingRateFrame)
										.build(),
						new DomainClientConfigBuilder<MarkPatch>(futuresFundingAndMarkEndpoint)
										.orchestrator(2, 0)
										.instanceConfig(100, messageHandler::parseMarkPriceMessageSymbol)
										.frames(frames::getSubscribeFuturesMarkPriceFrame, frames::getUnsubscribeFuturesMarkPriceFrame)
										.build(),
						new DomainClientConfigBuilder<BookTickerPatch>(spotEndpoint)
										.orchestrator(5, 0)
										.instanceConfig(100, messageHandler::parseSpotBookTickerMessageSymbol)
										.frames(frames::getSubscribeSpotBookTickerFrame, frames::getUnsubscribeSpotBookTickerFrame)
										.build()
		);
		super(config, new ProdModifiableSchedulerBuilder());
	}
}
