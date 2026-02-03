package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import lombok.NonNull;

public record WalletAddress(
        @NonNull SupportedChain chain, @NonNull String address, String memo) {}
