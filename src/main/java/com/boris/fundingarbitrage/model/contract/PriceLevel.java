package com.boris.fundingarbitrage.model.contract;

import com.boris.fundingarbitrage.model.Validations;

public record PriceLevel(double price, double volume) {
    public PriceLevel {
        Validations.requirePositive(price, "Price");
        Validations.requirePositive(volume, "Volume");
    }
}
