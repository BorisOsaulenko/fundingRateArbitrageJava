package com.boris.fundingarbitrage.intradelogic;

import com.boris.fundingarbitrage.monitor.CoinMonitor;

import java.math.BigDecimal;

public class TestInTradeLogic extends InTradeCoinLogic {
	public TestInTradeLogic(
					String coin,
					CoinMonitor monitor,
					BigDecimal legUsdtAmount
	) {
		super(coin, monitor, legUsdtAmount);
	}
}
