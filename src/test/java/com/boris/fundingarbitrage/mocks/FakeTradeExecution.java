package com.boris.fundingarbitrage.mocks;

import com.boris.fundingarbitrage.execution.ITradeExecution;
import com.boris.fundingarbitrage.execution.TradeIds;
import com.boris.fundingarbitrage.execution.factory.TradeExecutionFactory;
import com.boris.fundingarbitrage.model.exchange.snapshot.Snapshot;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

public class FakeTradeExecution implements ITradeExecution {
	public static final TradeIds TEST_ENTER_IDS = new TradeIds("longEnterId", "shortEnterId");
	public static final TradeIds TEST_EXIT_IDS = new TradeIds("longExitId", "shortExitId");

	private boolean enterShouldFail;
	private boolean exitShouldFail;
	private TradeIds enterIds;
	private TradeIds exitIds;

	public FakeTradeExecution() {
	}

	public static TradeExecutionFactory factory(FakeTradeExecution execution) {
		TradeExecutionFactory factory = Mockito.mock(TradeExecutionFactory.class);
		Mockito.when(factory.create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(execution);
		return factory;
	}

	public void enterFails() {
		enterShouldFail = true;
	}

	public void exitFails() {
		exitShouldFail = true;
	}

	public void reset() {
		enterShouldFail = false;
		exitShouldFail = false;
		enterIds = null;
		exitIds = null;
	}

	@Override
	public CompletableFuture<Void> enterTrade() {
		if (enterShouldFail) return CompletableFuture.failedFuture(new RuntimeException("enter failed"));
		if (enterIds != null) throw new IllegalStateException("Trade already entered.");

		enterIds = TEST_ENTER_IDS;
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<Void> exitTrade(Snapshot currLong, Snapshot currShort) {
		if (exitShouldFail) return CompletableFuture.failedFuture(new RuntimeException("exit failed"));
		if (exitIds != null) throw new IllegalStateException("Trade already exited.");
		if (enterIds == null) throw new IllegalStateException("Trade has not been entered.");

		exitIds = TEST_EXIT_IDS;
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public TradeIds getEnterIds() {
		return enterIds;
	}

	@Override
	public TradeIds getExitIds() {
		return exitIds;
	}
}
