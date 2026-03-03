package com.boris.fundingarbitrage.model.assetops;

import com.boris.fundingarbitrage.model.Validations;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import lombok.NonNull;

import java.math.BigDecimal;

public record Withdrawal(BigDecimal amount, BigDecimal fee, @NonNull WalletAddress address) {
	public Withdrawal {
		Validations.requirePositive(amount, "Amount");
	}
}
