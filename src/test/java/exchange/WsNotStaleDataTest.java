package exchange;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.exchange.Instances;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
	private static final Duration WAIT_DURATION = Duration.ofMinutes(2);
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
				futures.add(exchange.publicWsClient.connect());
			}
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

			for (BaseExchange exchange : exchanges) {
				ExchangeStats stats = statsByExchange.get(exchange);
				exchange.publicWsClient.subscribeBookTicker(COIN, stats::updateBookTicker);
				exchange.publicWsClient.subscribeFundingRates(COIN, stats::updateFundingRate);
				exchange.publicWsClient.subscribeMarkPrice(COIN, stats::updateMarkPrice);
			}

			TimeUnit.MILLISECONDS.sleep(WAIT_DURATION.toMillis());
		} finally {
			for (BaseExchange exchange : exchanges) {
				exchange.publicWsClient.close();
			}
		}

		List<String> failures = new ArrayList<>();
		for (BaseExchange exchange : exchanges) {
			failures.addAll(statsByExchange.get(exchange).validateChanges());
		}

		if (!failures.isEmpty()) {
			Logger.error("WsCorrectDataCheck failures:");
			for (String failure : failures) {
				Logger.error(failure);
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
		private final AtomicReference<BookTicker> latestBookTicker = new AtomicReference<>(BookTicker.empty());
		private final AtomicReference<FundingRate> latestFundingRate = new AtomicReference<>(FundingRate.empty());
		private final AtomicReference<MarkPrice> latestMarkPrice = new AtomicReference<>(MarkPrice.empty());

		private final FieldTracker<BigDecimal> bookBidPrice = new FieldTracker<>();
		private final FieldTracker<BigDecimal> bookBidSize = new FieldTracker<>();
		private final FieldTracker<BigDecimal> bookAskPrice = new FieldTracker<>();
		private final FieldTracker<BigDecimal> bookAskSize = new FieldTracker<>();
		private final FieldTracker<Instant> bookTimestamp = new FieldTracker<>();

		private final FieldTracker<BigDecimal> fundingRate = new FieldTracker<>();
		private final FieldTracker<Instant> fundingSettlement = new FieldTracker<>();
		private final FieldTracker<Instant> fundingTimestamp = new FieldTracker<>();

		private final FieldTracker<BigDecimal> markPrice = new FieldTracker<>();
		private final FieldTracker<Instant> markTimestamp = new FieldTracker<>();

		private ExchangeStats(BaseExchange exchange) {
			this.exchange = exchange;
		}

		private void updateBookTicker(BookTickerPatch patch) {
			BookTicker previous = latestBookTicker.get();
			BookTicker updated = new BookTicker(
							patch.bidPrice() != null ? patch.bidPrice() : previous.bidPrice(),
							patch.bidSize() != null ? patch.bidSize() : previous.bidSize(),
							patch.askPrice() != null ? patch.askPrice() : previous.askPrice(),
							patch.askSize() != null ? patch.askSize() : previous.askSize(),
							patch.timestamp()
			);
			latestBookTicker.set(updated);
			bookBidPrice.update(updated.bidPrice());
			bookBidSize.update(updated.bidSize());
			bookAskPrice.update(updated.askPrice());
			bookAskSize.update(updated.askSize());
			bookTimestamp.update(updated.timestamp());
		}

		private void updateFundingRate(FundingRatePatch patch) {
			FundingRate previous = latestFundingRate.get();
			FundingRate updated = new FundingRate(
							patch.rate() != null ? patch.rate() : previous.rate(),
							patch.settlement() != null ? patch.settlement() : previous.settlement(),
							patch.timestamp()
			);
			latestFundingRate.set(updated);
			fundingRate.update(updated.rate());
			fundingSettlement.update(updated.settlement());
			fundingTimestamp.update(updated.timestamp());
		}

		private void updateMarkPrice(MarkPricePatch patch) {
			MarkPrice updated = new MarkPrice(patch.price(), patch.timestamp());
			latestMarkPrice.set(updated);
			markPrice.update(updated.price());
			markTimestamp.update(updated.timestamp());
		}

		private List<String> validateChanges() {
			List<String> failures = new ArrayList<>();
			String prefix = exchange.name + " (" + COIN + ")";

			checkMinAmountChanges(failures, prefix, "BookTicker.bidPrice", bookBidPrice);
			checkMinAmountChanges(failures, prefix, "BookTicker.bidSize", bookBidSize);
			checkMinAmountChanges(failures, prefix, "BookTicker.askPrice", bookAskPrice);
			checkMinAmountChanges(failures, prefix, "BookTicker.askSize", bookAskSize);
			checkMinAmountChanges(failures, prefix, "BookTicker.timestamp", bookTimestamp);

			checkNoChanges(failures, prefix, "FundingRate.settlement", fundingSettlement);
			checkMinAmountChanges(failures, prefix, "FundingRate.timestamp", fundingTimestamp);

			checkMinAmountChanges(failures, prefix, "MarkPrice.price", markPrice);
			checkMinAmountChanges(failures, prefix, "MarkPrice.timestamp", markTimestamp);

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
