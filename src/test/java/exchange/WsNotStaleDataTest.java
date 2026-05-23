package exchange;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.exchange.publicws.FuturesHandler;
import com.boris.fundingarbitrage.exchange.publicws.SpotHandler;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Funding;
import com.boris.fundingarbitrage.model.contract.Mark;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WsNotStaleDataTest {
	private static final Logger log = LoggerFactory.getLogger(WsNotStaleDataTest.class);
	private static final Duration WAIT_DURATION = Duration.ofMinutes(3);
	private static final String COIN = "SOL";
	private static final int MIN_CHANGES = 3;

	@Test
	@Tag("integration")
	@Tag("websocket")
	public void testPublicWsDataChangesForAllExchanges() throws Exception {
		ArrayList<BaseExchange> exchanges = Instances.getExchangeArray();
		Map<BaseExchange, ExchangeStats> statsByExchange = new ConcurrentHashMap<>();

		for (BaseExchange exchange : exchanges) {
			statsByExchange.put(exchange, new ExchangeStats(exchange));
		}

		try {
			List<CompletableFuture<Void>> futures = new ArrayList<>();
			for (BaseExchange exchange : exchanges) {
				futures.add(exchange.publicWsClient().connect());
			}
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

			for (BaseExchange exchange : exchanges) {
				ExchangeStats stats = statsByExchange.get(exchange);
				FuturesHandler futuresHandler = new FuturesHandler(
								stats::updateFuturesBookTicker,
								stats::updateFuturesMarkPrice,
								stats::updateFuturesFundingRate
				);
				SpotHandler spotHandler = new SpotHandler(stats::updateSpotBookTicker);

				exchange.publicWsClient().subscribeFutures(COIN, futuresHandler);
				exchange.publicWsClient().subscribeSpot(COIN, spotHandler);
			}

			TimeUnit.MILLISECONDS.sleep(WAIT_DURATION.toMillis());
		} finally {
			for (BaseExchange exchange : exchanges) {
				exchange.publicWsClient().close();
			}
		}

		List<String> failures = new ArrayList<>();
		for (BaseExchange exchange : exchanges) {
			failures.addAll(statsByExchange.get(exchange).validateChanges());
		}

		if (!failures.isEmpty()) {
			log.error("WsCorrectDataCheck failures:");
			for (String failure : failures) {
				log.error(failure);
			}
			throw new Exception("WsCorrectDataCheck failed with " + failures.size() + " failing fields.");
		}
	}

	private static class FieldTracker<T> {
		private final AtomicReference<T> last = new AtomicReference<>();
		private final AtomicInteger changes = new AtomicInteger(-1);

		public void update(T value) {
			if (value == null) return;
			T previous = last.getAndSet(value);
			if (previous != null && !previous.equals(value)) {
				changes.incrementAndGet();
			}
		}

		public int changes() {
			return changes.get();
		}
	}

	private static class ExchangeStats {
		private final BaseExchange exchange;
		private final AtomicReference<BookTicker> latestSBookTicker = new AtomicReference<>(BookTicker.empty());
		private final AtomicReference<BookTicker> latestFBookTicker = new AtomicReference<>(BookTicker.empty());
		private final AtomicReference<Funding> latestFFundingRate = new AtomicReference<>(Funding.empty());
		private final AtomicReference<Mark> latestFMarkPrice = new AtomicReference<>(Mark.empty());

		private final FieldTracker<BigDecimal> sBookBidPrice = new FieldTracker<>();
		private final FieldTracker<BigDecimal> sBookBidSize = new FieldTracker<>();
		private final FieldTracker<BigDecimal> sBookAskPrice = new FieldTracker<>();
		private final FieldTracker<BigDecimal> sBookAskSize = new FieldTracker<>();
		private final FieldTracker<Instant> sBookTimestamp = new FieldTracker<>();

		private final FieldTracker<BigDecimal> fBookBidPrice = new FieldTracker<>();
		private final FieldTracker<BigDecimal> fBookBidSize = new FieldTracker<>();
		private final FieldTracker<BigDecimal> fBookAskPrice = new FieldTracker<>();
		private final FieldTracker<BigDecimal> fBookAskSize = new FieldTracker<>();
		private final FieldTracker<Instant> fBookTimestamp = new FieldTracker<>();

		private final FieldTracker<BigDecimal> fFundingRate = new FieldTracker<>();
		private final FieldTracker<Instant> fFundingSettlement = new FieldTracker<>();
		private final FieldTracker<Instant> fFundingTimestamp = new FieldTracker<>();

		private final FieldTracker<BigDecimal> fMarkPrice = new FieldTracker<>();
		private final FieldTracker<Instant> fMarkTimestamp = new FieldTracker<>();

		private ExchangeStats(BaseExchange exchange) {
			this.exchange = exchange;
		}

		private void updateSpotBookTicker(BookTickerPatch patch) {
			BookTicker previous = latestSBookTicker.get();
			BookTicker updated = new BookTicker(
							patch.bidPrice() != null ? patch.bidPrice() : previous.bidPrice(),
							patch.bidSize() != null ? patch.bidSize() : previous.bidSize(),
							patch.askPrice() != null ? patch.askPrice() : previous.askPrice(),
							patch.askSize() != null ? patch.askSize() : previous.askSize(),
							patch.timestamp()
			);
			latestSBookTicker.set(updated);
			sBookBidPrice.update(updated.bidPrice());
			sBookBidSize.update(updated.bidSize());
			sBookAskPrice.update(updated.askPrice());
			sBookAskSize.update(updated.askSize());
			sBookTimestamp.update(updated.timestamp());
		}

		private void updateFuturesBookTicker(BookTickerPatch patch) {
			BookTicker previous = latestFBookTicker.get();
			BookTicker updated = new BookTicker(
							patch.bidPrice() != null ? patch.bidPrice() : previous.bidPrice(),
							patch.bidSize() != null ? patch.bidSize() : previous.bidSize(),
							patch.askPrice() != null ? patch.askPrice() : previous.askPrice(),
							patch.askSize() != null ? patch.askSize() : previous.askSize(),
							patch.timestamp()
			);
			latestFBookTicker.set(updated);
			fBookBidPrice.update(updated.bidPrice());
			fBookBidSize.update(updated.bidSize());
			fBookAskPrice.update(updated.askPrice());
			fBookAskSize.update(updated.askSize());
			fBookTimestamp.update(updated.timestamp());
		}

		private void updateFuturesFundingRate(FundingPatch patch) {
			Funding previous = latestFFundingRate.get();
			Funding updated = new Funding(
							patch.rate() != null ? patch.rate() : previous.rate(),
							patch.settlement() != null ? patch.settlement() : previous.settlement(),
							patch.timestamp()
			);
			latestFFundingRate.set(updated);
			fFundingRate.update(updated.rate());
			fFundingSettlement.update(updated.settlement());
			fFundingTimestamp.update(updated.timestamp());
		}

		private void updateFuturesMarkPrice(MarkPatch patch) {
			Mark updated = new Mark(patch.price(), patch.timestamp());
			latestFMarkPrice.set(updated);
			fMarkPrice.update(updated.price());
			fMarkTimestamp.update(updated.timestamp());
		}

		private List<String> validateChanges() {
			List<String> failures = new ArrayList<>();
			String prefix = exchange.name() + " (" + COIN + ")";

			checkMinAmountChanges(failures, prefix, "SBookTicker.bidPrice", sBookBidPrice);
			checkMinAmountChanges(failures, prefix, "SBookTicker.bidSize", sBookBidSize);
			checkMinAmountChanges(failures, prefix, "SBookTicker.askPrice", sBookAskPrice);
			checkMinAmountChanges(failures, prefix, "SBookTicker.askSize", sBookAskSize);
			checkMinAmountChanges(failures, prefix, "SBookTicker.timestamp", sBookTimestamp);

			checkMinAmountChanges(failures, prefix, "FBookTicker.bidPrice", fBookBidPrice);
			checkMinAmountChanges(failures, prefix, "FBookTicker.bidSize", fBookBidSize);
			checkMinAmountChanges(failures, prefix, "FBookTicker.askPrice", fBookAskPrice);
			checkMinAmountChanges(failures, prefix, "FBookTicker.askSize", fBookAskSize);
			checkMinAmountChanges(failures, prefix, "FBookTicker.timestamp", fBookTimestamp);

			checkNoChanges(failures, prefix, "FFundingRate.settlement", fFundingSettlement);
			checkMinAmountChanges(failures, prefix, "FFundingRate.timestamp", fFundingTimestamp);

			checkMinAmountChanges(failures, prefix, "FMarkPrice.price", fMarkPrice);
			checkMinAmountChanges(failures, prefix, "FMarkPrice.timestamp", fMarkTimestamp);

			return failures;
		}

		private void checkMinAmountChanges(List<String> failures, String prefix, String field, FieldTracker<?> tracker) {
			if (tracker.changes() < MIN_CHANGES) {
				failures.add(prefix + " field " + field + " changed " + tracker.changes() + " times");
			}
		}

		private void checkNoChanges(List<String> failures, String prefix, String field, FieldTracker<?> tracker) {
			if (tracker.changes() > 0) {
				failures.add(prefix + " field " + field + " changed " + tracker.changes() + " times. Should have no changes.");
			}
		}
	}
}
