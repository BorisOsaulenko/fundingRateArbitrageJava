package com.boris.fundingarbitrage.coinfilter;

import com.boris.fundingarbitrage.coinparser.ICoinSupplier;
import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.exchange.privatews.PrivateWsClient;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesPublicOnePullData;
import com.boris.fundingarbitrage.exchange.publichttp.FuturesTradingState;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.SpotPublicOnePullData;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoinFilterTest {
	@Test
	void filterAsync_keepsCoinSupportedByExchangeOnBothMarkets() {
		String keptCoin = "BTC";
		String removedCoin = "DOGE";
		Set<String> coins = Set.of(keptCoin, removedCoin);
		ICoinSupplier coinSupplier = () -> CompletableFuture.completedFuture(coins);

		PublicHttpClient publicHttpClient = mock(PublicHttpClient.class);
		PrivateHttpClient privateHttpClient = mock(PrivateHttpClient.class);
		BaseExchange exchange = new BaseExchange(
						ExchangeName.BINANCE,
						mock(PublicWsClient.class),
						mock(PrivateWsClient.class),
						publicHttpClient,
						privateHttpClient
		);

		CoinVector<FuturesPublicOnePullData> futuresData = new CoinVector<>();
		futuresData.put(
						keptCoin, new FuturesPublicOnePullData(
										new BigDecimal("0.001"),
										new BigDecimal("5000000"),
										8,
										new BookTicker(
														new BigDecimal("99990"),
														new BigDecimal("2"),
														new BigDecimal("100000"),
														new BigDecimal("2"),
														Instant.now()
										),
										new Funding(new BigDecimal("0.0001"), Instant.now().plusSeconds(3600), Instant.now()),
										FuturesTradingState.TRADING
						)
		);
		futuresData.put(
						removedCoin, new FuturesPublicOnePullData(
										new BigDecimal("1"),
										new BigDecimal("10"),
										8,
										new BookTicker(
														new BigDecimal("0.1"),
														new BigDecimal("100"),
														new BigDecimal("0.11"),
														new BigDecimal("100"),
														Instant.now()
										),
										new Funding(new BigDecimal("0.0001"), Instant.now().plusSeconds(3600), Instant.now()),
										FuturesTradingState.TRADING
						)
		);

		CoinVector<SpotPublicOnePullData> spotData = new CoinVector<>();
		spotData.put(
						keptCoin, new SpotPublicOnePullData(
										new BigDecimal("0.001"),
										new BigDecimal("4000000"),
										new BookTicker(
														new BigDecimal("99980"),
														new BigDecimal("3"),
														new BigDecimal("99990"),
														new BigDecimal("3"),
														Instant.now()
										)
						)
		);
		spotData.put(
						removedCoin, new SpotPublicOnePullData(
										new BigDecimal("1"),
										new BigDecimal("10"),
										new BookTicker(
														new BigDecimal("0.1"),
														new BigDecimal("100"),
														new BigDecimal("0.11"),
														new BigDecimal("100"),
														Instant.now()
										)
						)
		);

		CoinVector<Fees> futuresFees = new CoinVector<>();
		futuresFees.put(keptCoin, Fees.allZero());
		futuresFees.put(removedCoin, Fees.allZero());
		CoinVector<Fees> spotFees = new CoinVector<>();
		spotFees.put(keptCoin, Fees.allZero());
		spotFees.put(removedCoin, Fees.allZero());

		when(publicHttpClient.getFuturesOnePullData(coins)).thenReturn(CompletableFuture.completedFuture(futuresData));
		when(publicHttpClient.getSpotOnePullData(coins)).thenReturn(CompletableFuture.completedFuture(spotData));
		when(privateHttpClient.getFutureTradingFees(coins)).thenReturn(CompletableFuture.completedFuture(futuresFees));
		when(privateHttpClient.getSpotTradingFees(coins)).thenReturn(CompletableFuture.completedFuture(spotFees));

		CoinFilter filter = new CoinFilter(
						coinSupplier,
						new CoinFilterConfig(new BigDecimal("1000"), new BigDecimal("1000")),
						Set.of(exchange)
		);

		CoinFilterResult result = filter.filterAsync().join();

		assertEquals(Set.of(exchange), result.coinAvailability().getExchanges(keptCoin));
		assertEquals(Set.of(keptCoin), result.coinAvailability().getCoins(exchange));
		assertTrue(result.coinAvailability().isFutures(exchange, keptCoin));
		assertTrue(result.coinAvailability().isSpot(exchange, keptCoin));
		assertNotNull(result.initialFuturesSnapshots().get(exchange, keptCoin));
		assertNotNull(result.initialSpotSnapshots().get(exchange, keptCoin));
		assertNotNull(result.constantDataRecord().getFuturesConstantData(exchange, keptCoin));
		assertNotNull(result.constantDataRecord().getSpotConstantData(exchange, keptCoin));
		assertFalse(result.coinAvailability().isFutures(exchange, removedCoin));
		assertFalse(result.coinAvailability().isSpot(exchange, removedCoin));
		assertNull(result.coinAvailability().getExchanges(removedCoin));
		assertTrue(result.coinAvailability().getExchanges().contains(exchange));
	}
}
