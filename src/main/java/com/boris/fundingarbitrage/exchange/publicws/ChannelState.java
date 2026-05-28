package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.scheduler.modifiable.IModifiableScheduler;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public record ChannelState<T extends GenericPublicWsPatch>(
				CoinVector<PublicWsInstance<T>> coinToInstanceMap,
				IModifiableScheduler pingScheduler,
				CoinVector<Consumer<T>> handlers,
				List<PublicWsInstance<T>> clients,
				AtomicInteger index
) {
}