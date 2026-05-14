package com.boris.fundingarbitrage.model.exchange.constantdata;

import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import lombok.NonNull;

import java.math.BigDecimal;

public record FuturesConstantData(
				@NonNull BigDecimal lotSize,
				@NonNull Fees fees,
				int fundingInterval
) implements ConstantData {
	@Override
	public TradeMarket market() {
		return TradeMarket.FUTURES;
	}
}