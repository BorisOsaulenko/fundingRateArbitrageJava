package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.Fees;
import lombok.NonNull;

import java.math.BigDecimal;

public record FuturesConstantData(
				@NonNull BigDecimal futuresLotSize,
				@NonNull Fees futuresFees,
				int fundingInterval
) {
}