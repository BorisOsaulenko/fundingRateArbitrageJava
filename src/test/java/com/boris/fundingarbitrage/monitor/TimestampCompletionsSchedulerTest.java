package com.boris.fundingarbitrage.monitor;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.mocks.FakeExchanges;
import com.boris.fundingarbitrage.mocks.FakeOneTimeScheduler;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class TimestampCompletionsSchedulerTest {
	private static final Instant initialTimestamp = Instant.now();
	private static final Instant completionTimestamp = initialTimestamp.plusSeconds(3);
	private static final Instant updateTimestamp = initialTimestamp.plusSeconds(1);
	private final static BookTicker updatedFuturesBookTicker = new BookTicker(
					new BigDecimal("121.00"),
					new BigDecimal("4.0"),
					new BigDecimal("121.20"),
					new BigDecimal("3.5"),
					updateTimestamp
	);
	private final static Funding updatedFuturesFunding = new Funding(
					new BigDecimal("0.00038"),
					initialTimestamp.plusSeconds(1900),
					updateTimestamp
	);
	private final static BookTicker updatedSpotBookTicker = new BookTicker(
					new BigDecimal("120.50"),
					new BigDecimal("5.0"),
					new BigDecimal("120.70"),
					new BigDecimal("4.5"),
					updateTimestamp
	);
	private final static Mark updatedFuturesMark = new Mark(new BigDecimal("121.10"), updateTimestamp);
	private static final String COIN = "BTC";
	private final static BookTicker initialFuturesBookTicker = new BookTicker(
					new BigDecimal("120.45"),
					new BigDecimal("4.2"),
					new BigDecimal("120.60"),
					new BigDecimal("3.8"),
					initialTimestamp
	);
	private final static Funding initialFuturesFunding = new Funding(
					new BigDecimal("0.00042"),
					initialTimestamp.plusSeconds(1800),
					initialTimestamp
	);
	private final static Mark initialFuturesMark = new Mark(new BigDecimal("120.52"), initialTimestamp);
	private final static BookTicker initialSpotBookTicker = new BookTicker(
					new BigDecimal("119.95"),
					new BigDecimal("5.1"),
					new BigDecimal("120.05"),
					new BigDecimal("4.6"),
					initialTimestamp
	);
	private final ExchangeCoinMap<Funding> futuresFunding = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> futuresBookTickers = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<Mark> futuresMark = new ExchangeCoinMap<>();
	private final ExchangeCoinMap<BookTicker> spotBookTickers = new ExchangeCoinMap<>();
	private final FakeOneTimeScheduler oneTimeScheduler = new FakeOneTimeScheduler();
	private TimestampCompletionsScheduler completionsScheduler;

	private void initExchange(BaseExchange ex) {
		futuresFunding.put(ex, COIN, initialFuturesFunding);
		futuresBookTickers.put(ex, COIN, initialFuturesBookTicker);
		futuresMark.put(ex, COIN, initialFuturesMark);
		spotBookTickers.put(ex, COIN, initialSpotBookTicker);
	}

	private void clear() {
		futuresFunding.clear();
		futuresBookTickers.clear();
		futuresMark.clear();
		spotBookTickers.clear();
	}

	@BeforeEach
	public void setUp() {
		clear();
		initExchange(FakeExchanges.exchange1);
		initExchange(FakeExchanges.exchange2);

		completionsScheduler = new TimestampCompletionsScheduler(
						futuresFunding,
						futuresBookTickers,
						futuresMark,
						spotBookTickers,
						oneTimeScheduler
		);
	}

	@AfterEach
	public void shutdown() {
		completionsScheduler.shutdown();
	}

	private void updateFuturesBookTicker(BaseExchange ex) {
		futuresBookTickers.put(ex, COIN, updatedFuturesBookTicker);
		completionsScheduler.processFuturesBookTickerUpdate(ex, COIN, updatedFuturesBookTicker);
	}

	private void updateFuturesFunding(BaseExchange ex) {
		futuresFunding.put(ex, COIN, updatedFuturesFunding);
		completionsScheduler.processFuturesFundingUpdate(ex, COIN, updatedFuturesFunding);
	}

	private void updateFuturesMark(BaseExchange ex) {
		futuresMark.put(ex, COIN, updatedFuturesMark);
		completionsScheduler.processFuturesMarkUpdate(ex, COIN, updatedFuturesMark);
	}

	private void updateSpotBookTicker(BaseExchange ex) {
		spotBookTickers.put(ex, COIN, updatedSpotBookTicker);
		completionsScheduler.processSpotBookTickerUpdate(ex, COIN, updatedSpotBookTicker);
	}

	@Test
	void performOnTimestamp_separatesDataByExchanges() {
		completionsScheduler.performOnTimestamp(
						completionTimestamp.toEpochMilli(), FakeExchanges.exchange1, COIN, (futures, spot) -> {
							assertNotNull(futures);
							assertNotNull(spot);
							assertEquals(initialFuturesBookTicker, futures.bookTicker());
							assertEquals(initialFuturesFunding, futures.funding());
							assertEquals(updatedFuturesMark, futures.mark());
							assertEquals(initialSpotBookTicker, spot.bookTicker());
						}
		);

		completionsScheduler.performOnTimestamp(
						completionTimestamp.toEpochMilli(), FakeExchanges.exchange2, COIN, (futures, spot) -> {
							assertNotNull(futures);
							assertNotNull(spot);
							assertEquals(updatedFuturesBookTicker, futures.bookTicker());
							assertEquals(initialFuturesFunding, futures.funding());
							assertEquals(initialFuturesMark, futures.mark());
							assertEquals(initialSpotBookTicker, spot.bookTicker());
						}
		);

		updateFuturesMark(FakeExchanges.exchange1);
		updateFuturesBookTicker(FakeExchanges.exchange2);
		oneTimeScheduler.doRunAll(); // skip await
	}

	@Test
	void cancelTimestampExecution_preventsCallbackExecution() {
		completionsScheduler.performOnTimestamp(
						completionTimestamp.toEpochMilli(), FakeExchanges.exchange1, COIN, (_, _) -> fail("Should not run")
		);

		completionsScheduler.cancelTimestampExecution(FakeExchanges.exchange1, COIN, completionTimestamp.toEpochMilli());
		oneTimeScheduler.doRunAll();
	}
}
