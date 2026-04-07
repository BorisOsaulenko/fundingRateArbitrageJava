package com.boris.fundingarbitrage.model.exchange.constantdata;

import com.boris.fundingarbitrage.model.contract.Fees;

import java.math.BigDecimal;

public sealed interface ConstantData permits FuturesConstantData, SpotConstantData {
	Fees fees();

	BigDecimal lotSize();

	default BigDecimal openTaker() {
		return fees().openTaker();
	}

	default BigDecimal closeTaker() {
		return fees().closeTaker();
	}

	default BigDecimal openMaker() {
		return fees().openMaker();
	}

	default BigDecimal closeMaker() {
		return fees().closeMaker();
	}
}