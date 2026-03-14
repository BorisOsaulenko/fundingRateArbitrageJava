package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

public record Fees(
				BigDecimal openMaker,
				BigDecimal openTaker,
				BigDecimal closeMaker,
				BigDecimal closeTaker,
				@NonNull Instant timestamp
) {
	public Fees {
		final BigDecimal maxReasonableFee = BigDecimal.valueOf(0.01); // 1%, should be much less
		BigDecimal maxFee = openMaker;
		for (BigDecimal fee : new BigDecimal[]{openTaker, closeMaker, closeTaker})
			if (fee.compareTo(maxFee) > 0) {
				maxFee = fee;
			}

		if (maxFee.compareTo(maxReasonableFee) > 0) {
			throw new IllegalArgumentException(String.format(
							"Fees very unlikely to be greater than 1%%. Got %.4f%%. May be collecting wrong data. Recheck, update maxReasonableFee if needed",
							maxFee.multiply(BigDecimal.valueOf(100))
			));
		}
	}

	public static Fees empty() {
		return new Fees(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.EPOCH);
	}

	public static boolean isPartiallyEmpty(Fees fees) {
		return fees.openMaker().equals(BigDecimal.ZERO) ||
					 fees.openTaker().equals(BigDecimal.ZERO) ||
					 fees.closeMaker().equals(BigDecimal.ZERO) ||
					 fees.closeTaker().equals(BigDecimal.ZERO) ||
					 Instant.EPOCH.equals(fees.timestamp());
	}

	public static Fees allZero() {
		return new Fees(
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						Instant.now()
		);
	}
}
