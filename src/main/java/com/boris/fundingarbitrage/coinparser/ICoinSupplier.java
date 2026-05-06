package com.boris.fundingarbitrage.coinparser;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ICoinSupplier {
	CompletableFuture<Set<String>> getCoinsAsync();
}
