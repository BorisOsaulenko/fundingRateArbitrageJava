package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.Validations;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import lombok.NonNull;

public record WithdrawChain(@NonNull SupportedChain chain, double withdrawFee, double minWithdraw) {
    public WithdrawChain {
        Validations.requirePositive(withdrawFee, "Withdraw fee");
        Validations.requirePositive(minWithdraw, "Minimum withdraw");
    }
}
