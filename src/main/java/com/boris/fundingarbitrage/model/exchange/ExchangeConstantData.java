package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.Fees;
import lombok.NonNull;

import java.math.BigDecimal;

public record ExchangeConstantData(
				@NonNull BigDecimal lotSize,
				@NonNull Fees fees,
				int fundingInterval
) {
}