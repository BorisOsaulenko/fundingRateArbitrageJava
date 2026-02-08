package com.boris.fundingarbitrage.model.contract;

import lombok.NonNull;

import java.time.Instant;

public record BookTicker(
				double bidPrice, double bidSize, double askPrice, double askSize, @NonNull Instant timestamp
) {}
