package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import org.mockito.Mockito;

import java.util.Set;

public final class FakeExchanges {
	public static BaseExchange exchange1 = mockExchange();
	public static BaseExchange exchange2 = mockExchange();
	public static BaseExchange exchange3 = mockExchange();

	public static Set<BaseExchange> threeExchanges() {
		return Set.of(exchange1, exchange2, exchange3);
	}

	public static Set<BaseExchange> twoExchanges() {
		return Set.of(exchange1, exchange2);
	}

	private static BaseExchange mockExchange() {
		return Mockito.mock(BaseExchange.class);
	}
}
