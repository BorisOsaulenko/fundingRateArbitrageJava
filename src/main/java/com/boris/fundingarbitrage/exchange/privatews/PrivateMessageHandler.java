package com.boris.fundingarbitrage.exchange.privatews;

import com.boris.fundingarbitrage.model.contract.PartialFill;
import com.boris.fundingarbitrage.model.websocket.patch.DepositPatch;
import com.fasterxml.jackson.databind.JsonNode;

public interface PrivateMessageHandler {
	DepositPatch parseDepositMessageSymbol(JsonNode root);

	PartialFill parsePartialFillMessageSymbol(JsonNode root);

	String getResponseToPingMessage(String message) throws Exception;
}
