package com.boris.fundingarbitrage.model.exchange.constantdata;

import com.boris.fundingarbitrage.model.contract.Fees;
import lombok.NonNull;

import java.math.BigDecimal;

public record SpotConstantData(
				@NonNull BigDecimal lotSize,
				@NonNull Fees fees
) implements ConstantData {
}
