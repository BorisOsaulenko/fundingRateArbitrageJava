package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.Fees;
import lombok.NonNull;

import java.math.BigDecimal;

public record SpotConstantData(
				@NonNull BigDecimal spotLotSize,
				@NonNull Fees spotFees
) {
}
