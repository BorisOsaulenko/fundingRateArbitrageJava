package com.boris.fundingarbitrage.exchange.impl.binance.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;

import java.util.Map;

class ChainsMap {
	private static final Map<SupportedChain, String> forward = Map.of(
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
					"ETH"
	);

	private static final Map<String, SupportedChain> inverse = Map.of(
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
					SupportedChain.ERC
	);

	public static String get(SupportedChain chainName) {
		return forward.get(chainName);
	}

	public static SupportedChain getInverse(String chainName) {
		return inverse.get(chainName);
	}
}
