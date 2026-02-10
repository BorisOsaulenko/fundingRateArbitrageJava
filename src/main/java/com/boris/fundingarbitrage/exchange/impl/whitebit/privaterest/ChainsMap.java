package com.boris.fundingarbitrage.exchange.impl.whitebit.privaterest;

import com.boris.fundingarbitrage.model.assetops.SupportedChain;

import java.util.HashMap;
import java.util.Map;

public final class ChainsMap {
	private static final Map<SupportedChain, String> forward = new HashMap<>();
	private static final Map<String, SupportedChain> inverse = new HashMap<>();

	static {
		register(SupportedChain.ERC, "ERC20");
		register(SupportedChain.TRX, "TRC20");
		register(SupportedChain.BSC, "BEP20");
		register(SupportedChain.POLYGON, "POLYGON");
		register(SupportedChain.POLYGON, "MATIC");
		register(SupportedChain.ARBITRUM, "ARBITRUM");
		register(SupportedChain.AVAX, "AVAXC");
		register(SupportedChain.AVAX, "AVAX");
		register(SupportedChain.SOL, "SOL");
		register(SupportedChain.APTOS, "APTOS");
		register(SupportedChain.NEAR, "NEAR");
		register(SupportedChain.TON, "TON");
	}

	private static void register(SupportedChain chain, String name) {
		forward.put(chain, name);
		inverse.put(name.toUpperCase(), chain);
	}

	public static String get(SupportedChain chain) {
		return forward.get(chain);
	}

	public static SupportedChain getInverse(String chainName) {
		if (chainName == null) return null;
		return inverse.get(chainName.toUpperCase());
	}
}
