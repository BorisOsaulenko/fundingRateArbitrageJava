package com.boris.fundingarbitrage.exchange.publicws;

import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;

import java.util.function.Consumer;

public record SpotHandler(
				Consumer<BookTickerPatch> bookTickerHandler
) {
}
