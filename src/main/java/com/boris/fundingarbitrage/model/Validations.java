package com.boris.fundingarbitrage.model;

public class Validations {
    public static void requirePositive(double value, String fieldName) {
        if (value <= 0) throw new IllegalArgumentException(fieldName + " must be positive.");
    }
}
