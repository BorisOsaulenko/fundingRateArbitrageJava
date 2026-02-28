package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;

import java.math.BigDecimal;

public record PriceLevel(BigDecimal price, BigDecimal volume) {
	public PriceLevel {
		Validations.requirePositive(price, "Price");
		Validations.requirePositive(volume, "Volume");
	}
}
