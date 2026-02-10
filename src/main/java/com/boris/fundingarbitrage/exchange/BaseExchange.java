package com.boris.fundingarbitrage.exchange;

import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import org.jetbrains.annotations.NotNull;

public abstract class BaseExchange {
	public final PublicWsClient publicWsClient;
	public final PrivateWsClient privateWsClient;
	public final PublicHttpClient publicHttpClient;
	public final PrivateHttpClient privateHttpClient;

	public BaseExchange(
					PublicWsClient publicWsClient,
					PrivateWsClient privateWsClient,
					@NotNull PublicHttpClient publicHttpClient,
					@NotNull PrivateHttpClient privateHttpClient
	) {
		this.publicWsClient = publicWsClient;
		this.privateWsClient = privateWsClient;
		this.publicHttpClient = publicHttpClient;
		this.privateHttpClient = privateHttpClient;
	}
}
