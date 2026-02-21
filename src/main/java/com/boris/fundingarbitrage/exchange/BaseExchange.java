package com.boris.fundingarbitrage.exchange;

import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import privatehttp.PrivateHttpClient;

public abstract class BaseExchange {
	public final ExchangeName name;
	public final PublicWsClient publicWsClient;
	public final PrivateWsClient privateWsClient;
	public final PublicHttpClient publicHttpClient;
	public final PrivateHttpClient privateHttpClient;

	public BaseExchange(
					@NonNull ExchangeName name,
					@NonNull PublicWsClient publicWsClient,
					@NotNull PrivateWsClient privateWsClient,
					@NotNull PublicHttpClient publicHttpClient,
					@NotNull PrivateHttpClient privateHttpClient
	) {
		this.name = name;
		this.publicWsClient = publicWsClient;
		this.privateWsClient = privateWsClient;
		this.publicHttpClient = publicHttpClient;
		this.privateHttpClient = privateHttpClient;
	}
}
