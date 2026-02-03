package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.impl.bybit.BybitExchange;
import com.boris.fundingarbitrage.util.logger.Logger;

class App {
	static void main(String[] args) throws Exception {
		BybitExchange bybitExchange = new BybitExchange();
		bybitExchange.publicWsClient.subscribeFundingRates(
						"ZIL", (rate) -> {
							Logger.getInstance().log(rate.toString());
						}
		);

		Thread.sleep(90000);
	}
}
