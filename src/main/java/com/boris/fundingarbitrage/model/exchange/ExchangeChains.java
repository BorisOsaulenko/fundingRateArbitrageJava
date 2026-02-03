package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import java.util.List;

public record ExchangeChains(List<SupportedChain> depositableChains, List<WithdrawChain> withdrawableChains) {}
