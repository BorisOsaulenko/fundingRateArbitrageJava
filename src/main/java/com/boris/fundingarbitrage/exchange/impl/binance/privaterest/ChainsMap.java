package com.boris.fundingarbitrage.exchange.impl.binance.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import java.util.Map;

public class ChainsMap {
    public static Map<SupportedChain, String> get() {
        return Map.of(
                SupportedChain.BSC,
                "BSC",
                SupportedChain.NEAR,
                "NEAR",
                SupportedChain.TON,
                "TON",
                SupportedChain.ARBITRUM,
                "ARBITRUM",
                SupportedChain.SOL,
                "SOL",
                SupportedChain.TRX,
                "TRX",
                SupportedChain.APTOS,
                "APT",
                SupportedChain.POLYGON,
                "MATIC",
                SupportedChain.AVAX,
                "AVAXC",
                SupportedChain.ERC,
                "ETH");
    }

    public static Map<String, SupportedChain> getInverse() {
        return Map.of(
                "BSC",
                SupportedChain.BSC,
                "NEAR",
                SupportedChain.NEAR,
                "TON",
                SupportedChain.TON,
                "ARBITRUM",
                SupportedChain.ARBITRUM,
                "SOL",
                SupportedChain.SOL,
                "TRX",
                SupportedChain.TRX,
                "APT",
                SupportedChain.APTOS,
                "MATIC",
                SupportedChain.POLYGON,
                "AVAXC",
                SupportedChain.AVAX,
                "ETH",
                SupportedChain.ERC);
    }
}
