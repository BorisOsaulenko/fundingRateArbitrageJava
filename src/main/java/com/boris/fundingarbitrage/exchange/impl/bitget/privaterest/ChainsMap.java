package com.boris.fundingarbitrage.exchange.impl.bitget.privaterest;

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
		register(SupportedChain.ARBITRUM, "ARBITRUM");
		register(SupportedChain.AVAX, "AVAXC");
		register(SupportedChain.SOL, "SOL");
		register(SupportedChain.APTOS, "APTOS");
		register(SupportedChain.NEAR, "NEAR");
		register(SupportedChain.TON, "TON");
	}

	private static void register(SupportedChain chain, String name) {
		forward.put(chain, name);
		inverse.put(normalize(name), chain);
	}

	public static Map<SupportedChain, String> get() {
		return forward;
	}

	public static SupportedChain fromChainName(String chainName) {
		if (chainName == null) return null;
		return inverse.get(normalize(chainName));
	}

	private static String normalize(String chainName) {
		return chainName.replace("-", "").replace("_", "").replace(" ", "").toUpperCase();
	}
}
