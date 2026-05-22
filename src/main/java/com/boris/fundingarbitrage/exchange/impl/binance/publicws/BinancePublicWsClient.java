package com.boris.fundingarbitrage.exchange.impl.binance.publicws;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.publicws.ClientsConfig;
import com.boris.fundingarbitrage.exchange.publicws.FuturesHandler;
import com.boris.fundingarbitrage.exchange.publicws.IPublicWsFrames;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.scheduler.ProdModifiableSchedulerBuilder;

import java.net.URI;
import java.util.Set;
import java.util.function.Consumer;

public class BinancePublicWsClient extends PublicWsClient {
	public BinancePublicWsClient(ExchangeContext context) {
		URI futuresFundingAndMarkEndpoint = URI.create("wss://fstream.binance.com/market");
		URI futuresBookTickerEndpoint = URI.create("wss://fstream.binance.com/public/ws");
		URI spotEndpoint = URI.create("wss://stream.binance.com:9443/ws");
		MessageHandler messageHandler = new MessageHandler(context);
		IPublicWsFrames wsFrames = new WsFrames();
		ClientsConfig config = new ClientsConfig(
						spotEndpoint,
						futuresBookTickerEndpoint,
						futuresFundingAndMarkEndpoint,
						futuresFundingAndMarkEndpoint,
						5,
						5,
						50,
						50,
						0,
						0
		);
		super(context, config, wsFrames, messageHandler, new ProdModifiableSchedulerBuilder());
	}

	@Override
	public void subscribeFutures(Set<String> coins, FuturesHandler handler) {
		super.subscribeFuturesFundingRates(coins, handler.fundingRateHandler());
		super.subscribeFuturesBookTicker(coins, handler.bookTickerHandler());
		coins.forEach(coin -> this.futuresMarkPriceHandlers.put(coin, handler.markPriceHandler()));
	}

	@Override
	protected void subscribeFuturesMarkPrice(Set<String> coins, Consumer<MarkPatch> handler) {
	}
}
