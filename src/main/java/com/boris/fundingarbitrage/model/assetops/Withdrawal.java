package com.boris.fundingarbitrage.model.assetops;

import com.boris.fundingarbitrage.model.Validations;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import lombok.NonNull;

public record Withdrawal(double amount, @NonNull WalletAddress address) {
    public Withdrawal {
        Validations.requirePositive(amount, "Amount");
    }
}
