package com.boris.fundingarbitrage;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import org.mockito.Mockito;

import java.util.Set;

public final class FakeExchanges {
	private static BaseExchange fakeExchange1;
	private static BaseExchange fakeExchange2;
	private static BaseExchange fakeExchange3;
	private FakeExchanges() {
	}

	public static BaseExchange exchange1() {
		return fakeExchange1;
	}

	public static BaseExchange exchange2() {
		return fakeExchange2;
	}

	public static BaseExchange exchange3() {
		return fakeExchange3;
	}

	public static Set<BaseExchange> threeExchanges() {
		return Set.of(fakeExchange1, fakeExchange2, fakeExchange3);
	}

	public static Set<BaseExchange> twoExchanges() {
		return Set.of(fakeExchange1, fakeExchange2);
	}

	private BaseExchange mockExchange() {
		return Mockito.mock(BaseExchange.class);
	}
}
