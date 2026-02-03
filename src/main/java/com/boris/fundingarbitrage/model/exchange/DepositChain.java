package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import lombok.NonNull;

public record DepositChain(@NonNull SupportedChain chain, String memo) {}
