package com.boris.fundingarbitrage.model.assetops;

import com.boris.fundingarbitrage.model.Validations;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import lombok.NonNull;

public record Withdrawal(int amount, double fee, @NonNull WalletAddress address) {
	public Withdrawal {
		Validations.requirePositive(amount, "Amount");
	}
}
