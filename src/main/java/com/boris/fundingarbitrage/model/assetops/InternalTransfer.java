package com.boris.fundingarbitrage.model.assetops;

import com.boris.fundingarbitrage.model.Validations;

import java.math.BigDecimal;

public record InternalTransfer(InternalAccount from, InternalAccount to, BigDecimal amount) {
	public InternalTransfer {
		if (from == to) {
			throw new IllegalArgumentException("Internal transfer from and to accounts cannot be the same.");
		}

		Validations.requirePositive(amount, "Amount");
	}
}
