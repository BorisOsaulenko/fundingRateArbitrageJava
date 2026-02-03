package com.boris.fundingarbitrage.model.exchange;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import java.util.ArrayList;

public class ExchangeChainsBuilder {
    private final ArrayList<SupportedChain> depositableChains = new ArrayList<>();
    private final ArrayList<WithdrawChain> withdrawableChains = new ArrayList<>();

    public ExchangeChainsBuilder() {}

    public ExchangeChainsBuilder addDepositableChain(SupportedChain depositChain) {
        this.depositableChains.add(depositChain);
        return this;
    }

    public ExchangeChainsBuilder addWithdrawableChain(WithdrawChain withdrawChain) {
        this.withdrawableChains.add(withdrawChain);
        return this;
    }

    public ExchangeChains build() {
        return new ExchangeChains(depositableChains, withdrawableChains);
    }
}
