package com.boris.fundingarbitrage.model;

import java.math.BigDecimal;

public class Validations {
	public static void requirePositive(double value, String fieldName) {
		if (value <= 0) throw new IllegalArgumentException(fieldName + " must be positive.");
	}

	public static void requireNonNegative(double value, String fieldName) {
		if (value < 0) throw new IllegalArgumentException(fieldName + " must be non-negative.");
	}

	public static void requireNonNegative(BigDecimal value, String fieldName) {
		if (value.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException(fieldName + " must be non-negative.");
	}
}
