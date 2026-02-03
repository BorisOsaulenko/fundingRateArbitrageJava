package com.boris.fundingarbitrage.exchange.privatews;

import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;

public interface PrivateMessageHandler {
	DepositPatch parseDepositMessageSymbol(String message);

	PartialFill parsePartialFillMessageSymbol(String message);

	String getResponseToPingMessage(String message);
}
