package com.boris.fundingarbitrage.mocks;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.IPublicMarketDataStream;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import org.mockito.Mockito;

import java.util.Set;

public final class FakeExchanges {
	public static BaseExchange exchange1 = mockExchange("exchange1");
	public static BaseExchange exchange2 = mockExchange("exchange2");
	public static BaseExchange exchange3 = mockExchange("exchange3");

	public static Set<BaseExchange> threeExchanges() {
		return Set.of(exchange1, exchange2, exchange3);
	}

	public static Set<BaseExchange> twoExchanges() {
		return Set.of(exchange1, exchange2);
	}

	private static BaseExchange mockExchange(String exchangeName) {
		ExchangeName nameMock = Mockito.mock(ExchangeName.class);
		Mockito.when(nameMock.toString()).thenReturn(exchangeName);

		return new BaseExchange(
						nameMock,
						Mockito.mock(IPublicMarketDataStream.class),
						Mockito.mock(PublicHttpClient.class),
						Mockito.mock(PrivateHttpClient.class)
		);
	}
}
