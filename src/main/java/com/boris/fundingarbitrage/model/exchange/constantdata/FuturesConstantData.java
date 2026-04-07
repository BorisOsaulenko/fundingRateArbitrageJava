package com.boris.fundingarbitrage.model.exchange.constantdata;

import com.boris.fundingarbitrage.model.contract.Fees;
import lombok.NonNull;

import java.math.BigDecimal;

public record FuturesConstantData(
				@NonNull BigDecimal lotSize,
				@NonNull Fees fees,
				int fundingInterval
) implements ConstantData {
}