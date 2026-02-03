package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import lombok.NonNull;

public record ExchangeSnapshot(
        @NonNull BookTicker bookTicker,
        @NonNull Fees fees,
        @NonNull FundingRate fundingRate,
        @NonNull MarkPrice markPrice) {}
