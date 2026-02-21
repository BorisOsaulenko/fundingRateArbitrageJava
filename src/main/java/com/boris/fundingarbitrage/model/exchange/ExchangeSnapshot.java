package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;

import java.math.BigDecimal;

public record ExchangeSnapshot(
				BookTicker bookTicker, Fees fees, FundingRate fundingRate, MarkPrice markPrice, BigDecimal lotSize
) {}