package execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.execution.CoinExecution;
import com.boris.fundingarbitrage.model.assetops.FuturesOrder;
import com.boris.fundingarbitrage.model.assetops.OrderSide;
import com.boris.fundingarbitrage.model.assetops.TradeParams;
import com.boris.fundingarbitrage.model.assetops.TradeSide;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ExecutionTest {
	private static final String COIN = "SOL";

	private static <T> CompletableFuture<T> failedFuture(Throwable t) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.completeExceptionally(t);
		return future;
	}

	@Test
	public void enterTrade_isIdempotent_andSetsEnterIds() {
		PrivateHttpClient longClient = mock(PrivateHttpClient.class);
		PrivateHttpClient shortClient = mock(PrivateHttpClient.class);

		when(longClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(CompletableFuture.completedFuture("L1"));
		when(shortClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(CompletableFuture.completedFuture("S1"));

		TradeParams params = new TradeParams(
						new TestExchange(ExchangeName.BINANCE, longClient),
						new TestExchange(ExchangeName.OKX, shortClient),
						BigDecimal.ONE,
						1,
						1
		);
		CoinExecution execution = new CoinExecution(COIN, params, 5);

		CompletableFuture<Void> first = execution.enterTrade();
		CompletableFuture<Void> second = execution.enterTrade();

		assertSame(first, second);
		first.join();
		assertNotNull(execution.getEnterIds());
		assertEquals("L1", execution.getEnterIds().longId());
		assertEquals("S1", execution.getEnterIds().shortId());

		verify(longClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.LONG && order.tradeSide() == TradeSide.OPEN)
		);
		verify(shortClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.SHORT && order.tradeSide() == TradeSide.OPEN)
		);
	}

	@Test
	public void exitTrade_isIdempotent_andRequiresSuccessfulEnter() {
		PrivateHttpClient longClient = mock(PrivateHttpClient.class);
		PrivateHttpClient shortClient = mock(PrivateHttpClient.class);

		when(longClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(
										CompletableFuture.completedFuture("L1"),
										CompletableFuture.completedFuture("L2")
						);
		when(shortClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(
										CompletableFuture.completedFuture("S1"),
										CompletableFuture.completedFuture("S2")
						);

		TradeParams params = new TradeParams(
						new TestExchange(ExchangeName.BINANCE, longClient),
						new TestExchange(ExchangeName.OKX, shortClient),
						BigDecimal.ONE,
						1,
						1
		);
		CoinExecution execution = new CoinExecution(COIN, params, 5);

		execution.exitTrade().join();
		verify(longClient, never()).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.tradeSide() == TradeSide.CLOSE)
		);
		verify(shortClient, never()).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.tradeSide() == TradeSide.CLOSE)
		);

		execution.enterTrade().join();
		CompletableFuture<Void> first = execution.exitTrade();
		CompletableFuture<Void> second = execution.exitTrade();
		assertSame(first, second);
		first.join();
		assertNotNull(execution.getExitIds());
		assertEquals("L2", execution.getExitIds().longId());
		assertEquals("S2", execution.getExitIds().shortId());

		verify(longClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.LONG && order.tradeSide() == TradeSide.OPEN)
		);
		verify(longClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.LONG && order.tradeSide() == TradeSide.CLOSE)
		);
		verify(shortClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.SHORT && order.tradeSide() == TradeSide.OPEN)
		);
		verify(shortClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.SHORT && order.tradeSide() == TradeSide.CLOSE)
		);
	}

	@Test
	public void enterTrade_longFailure_compensatesShort_andBlocksFutureExit() {
		PrivateHttpClient longClient = mock(PrivateHttpClient.class);
		PrivateHttpClient shortClient = mock(PrivateHttpClient.class);

		CompletableFuture<String> longFail = failedFuture(new RuntimeException("long-fail"));
		CompletableFuture<String> shortOpen = CompletableFuture.completedFuture("S1");
		CompletableFuture<String> shortClose = CompletableFuture.completedFuture("S2");

		when(longClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(longFail);
		when(shortClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(shortOpen, shortClose);

		TradeParams params = new TradeParams(
						new TestExchange(ExchangeName.BINANCE, longClient),
						new TestExchange(ExchangeName.OKX, shortClient),
						BigDecimal.ONE,
						1,
						1
		);
		CoinExecution execution = new CoinExecution(COIN, params, 5);

		CompletionException error = assertThrows(CompletionException.class, () -> execution.enterTrade().join());
		assertInstanceOf(IllegalStateException.class, error.getCause());
		assertTrue(error.getCause().getMessage().contains("long enter failed"));

		verify(shortClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.SHORT && order.tradeSide() == TradeSide.OPEN)
		);
		verify(shortClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.SHORT && order.tradeSide() == TradeSide.CLOSE)
		);
		verify(longClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.LONG && order.tradeSide() == TradeSide.OPEN)
		);
		verify(longClient, never()).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.tradeSide() == TradeSide.CLOSE)
		);

		execution.exitTrade().join();
		verifyNoMoreInteractions(longClient, shortClient);
	}

	@Test
	public void enterTrade_shortFailure_compensatesLong_andBlocksFutureExit() {
		PrivateHttpClient longClient = mock(PrivateHttpClient.class);
		PrivateHttpClient shortClient = mock(PrivateHttpClient.class);

		CompletableFuture<String> longOpen = CompletableFuture.completedFuture("L1");
		CompletableFuture<String> longClose = CompletableFuture.completedFuture("L2");
		CompletableFuture<String> shortFail = failedFuture(new RuntimeException("short-fail"));

		when(longClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(longOpen, longClose);
		when(shortClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(shortFail);

		TradeParams params = new TradeParams(
						new TestExchange(ExchangeName.BINANCE, longClient),
						new TestExchange(ExchangeName.OKX, shortClient),
						BigDecimal.ONE,
						1,
						1
		);
		CoinExecution execution = new CoinExecution(COIN, params, 5);

		CompletionException error = assertThrows(CompletionException.class, () -> execution.enterTrade().join());
		assertInstanceOf(IllegalStateException.class, error.getCause());
		assertTrue(error.getCause().getMessage().contains("short enter failed"));

		verify(longClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.LONG && order.tradeSide() == TradeSide.OPEN)
		);
		verify(longClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.LONG && order.tradeSide() == TradeSide.CLOSE)
		);
		verify(shortClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.SHORT && order.tradeSide() == TradeSide.OPEN)
		);
		verify(shortClient, never()).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.tradeSide() == TradeSide.CLOSE)
		);

		execution.exitTrade().join();
		verifyNoMoreInteractions(longClient, shortClient);
	}

	@Test
	public void enterTrade_bothLegsFail_noCompensation() {
		PrivateHttpClient longClient = mock(PrivateHttpClient.class);
		PrivateHttpClient shortClient = mock(PrivateHttpClient.class);

		CompletableFuture<String> longFail = failedFuture(new RuntimeException("long-fail"));
		CompletableFuture<String> shortFail = failedFuture(new RuntimeException("short-fail"));

		when(longClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(longFail);
		when(shortClient.placeFuturesOrder(eq(COIN), any(FuturesOrder.class)))
						.thenReturn(shortFail);

		TradeParams params = new TradeParams(
						new TestExchange(ExchangeName.BINANCE, longClient),
						new TestExchange(ExchangeName.OKX, shortClient),
						BigDecimal.ONE,
						1,
						1
		);
		CoinExecution execution = new CoinExecution(COIN, params, 5);

		assertThrows(CompletionException.class, () -> execution.enterTrade().join());

		verify(longClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.LONG && order.tradeSide() == TradeSide.OPEN)
		);
		verify(shortClient, times(1)).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.orderSide() == OrderSide.SHORT && order.tradeSide() == TradeSide.OPEN)
		);
		verify(longClient, never()).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.tradeSide() == TradeSide.CLOSE)
		);
		verify(shortClient, never()).placeFuturesOrder(
						eq(COIN),
						argThat(order -> order.tradeSide() == TradeSide.CLOSE)
		);
	}

	private static class TestExchange extends BaseExchange {
		private TestExchange(ExchangeName name, PrivateHttpClient privateHttpClient) {
			super(
							name,
							mock(PublicWsClient.class),
							mock(PrivateWsClient.class),
							mock(PublicHttpClient.class),
							privateHttpClient
			);
		}
	}
}
