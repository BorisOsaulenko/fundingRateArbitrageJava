package com.boris.fundingarbitrage.model.exchange.constantdata;

import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.strategy.TradeMarket;
import lombok.NonNull;

import java.math.BigDecimal;

public sealed interface ConstantData permits FuturesConstantData, SpotConstantData {
	@NonNull
	Fees fees();

	@NonNull
	BigDecimal lotSize();

	TradeMarket market();

	default @NonNull BigDecimal openTaker() {
		return fees().openTaker();
	}

	default @NonNull BigDecimal closeTaker() {
		return fees().closeTaker();
	}

	default @NonNull BigDecimal openMaker() {
		return fees().openMaker();
	}

	default @NonNull BigDecimal closeMaker() {
		return fees().closeMaker();
	}
}