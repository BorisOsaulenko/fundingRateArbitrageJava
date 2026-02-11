package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;
import org.apache.commons.lang3.math.NumberUtils;

import java.time.Instant;

public class Fees {
	public double openMaker;
	public double openTaker;
	public double closeMaker;
	public double closeTaker;
	public Instant timestamp;

	public Fees(double openMaker, double openTaker, double closeMaker, double closeTaker, @NonNull Instant timestamp) {
		final double maxReasonableFee = 0.01; // 1%, should be much less
		final double maxFee = NumberUtils.max(openMaker, openTaker, closeMaker, closeTaker);
		if (maxFee > maxReasonableFee) {
			throw new IllegalArgumentException(String.format(
							"Fees very unlikely to be greater than 1%%. Got %.4f%%. May be collecting wrong data. Recheck, update maxReasonableFee if needed",
							maxFee * 100
			));
		}
	}

	public static Fees empty() {
		return new Fees(0, 0, 0, 0, Instant.EPOCH);
	}
}
