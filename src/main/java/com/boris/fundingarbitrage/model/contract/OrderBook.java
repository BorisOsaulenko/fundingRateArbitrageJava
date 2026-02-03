package com.boris.fundingarbitrage.model.contract;

import java.util.List;
import lombok.NonNull;

public record OrderBook(
        @NonNull List<PriceLevel> bids, @NonNull List<PriceLevel> asks) {}
