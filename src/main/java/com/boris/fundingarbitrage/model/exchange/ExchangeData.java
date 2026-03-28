package com.boris.fundingarbitrage.model.exchange;

import lombok.NonNull;

public record ExchangeData(
				@NonNull FuturesConstantData futuresConstantData,
				@NonNull FuturesSnapshot futuresSnapshot,
				@NonNull SpotConstantData spotConstantData,
				@NonNull SpotSnapshot spotSnapshot
) {
	public ExchangeData(ExchangeSnapshot sn, ExchangeConstantData cd) {
		this(cd.futuresConstantData(), sn.futuresSnapshot(), cd.spotConstantData(), sn.spotSnapshot());
	}

	public ExchangeConstantData constantData() {
		return new ExchangeConstantData(futuresConstantData(), spotConstantData());
	}
}