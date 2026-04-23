package com.boris.fundingarbitrage.exchange;

import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

public record BaseExchange(
				@NonNull ExchangeName name,
				@NonNull PublicWsClient publicWsClient,
				@NotNull PrivateWsClient privateWsClient,
				@NotNull PublicHttpClient publicHttpClient,
				@NotNull PrivateHttpClient privateHttpClient
) {
}
