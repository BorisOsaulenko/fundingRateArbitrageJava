package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.Validations;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import lombok.NonNull;

import java.math.BigDecimal;

public record WithdrawChain(
				@NonNull SupportedChain chain, BigDecimal withdrawFee, BigDecimal minWithdraw, int precisionPoints
) {
	public WithdrawChain {
		Validations.requireNonNegative(withdrawFee, "Withdraw fee");
		Validations.requireNonNegative(minWithdraw, "Minimum withdraw");
		Validations.requirePositive(precisionPoints, "Precision points");
	}
}
