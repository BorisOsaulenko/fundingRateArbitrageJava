package exchange;

import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingRatePatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPricePatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Tag("integration")
public abstract class PublicWsTest {
	private static final Set<String> COINS = Set.of("SOL", "KAITO");
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(240);
	private static final int MIN_MESSAGES_PER_STREAM = 3;
	private final CoinVector<Integer> bookTickerMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> fundingRateMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> markPriceMessageCounts = new CoinVector<>();
	private final CoinVector<BookTickerPatch> latestBookTickerPatches = new CoinVector<>();
	private final CoinVector<FundingRatePatch> latestFundingRatePatches = new CoinVector<>();
	private final CoinVector<MarkPricePatch> latestMarkPricePatches = new CoinVector<>();

	private CompletableFuture<Void> waitingFuture;

	protected abstract PublicWsClient publicWsClient();

	private void initializeMessageCounts() {
		for (String coin : COINS) {
			bookTickerMessageCounts.put(coin, 0);
			fundingRateMessageCounts.put(coin, 0);
			markPriceMessageCounts.put(coin, 0);
		}
	}

	private void checkMessages() {
		if (bookTickerMessageCounts.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM).isEmpty() &&
				fundingRateMessageCounts.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM).isEmpty() &&
				markPriceMessageCounts.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM).isEmpty()) {
			this.waitingFuture.complete(null);
		}
	}

	private void updateBookTickerPatch(BookTickerPatch patch) {
		latestBookTickerPatches.merge(
						patch.coin(), patch, (existing, incoming) -> {
							return new BookTickerPatch(
											incoming.coin(),
											incoming.bidPrice() != null ? incoming.bidPrice() : existing.bidPrice(),
											incoming.bidSize() != null ? incoming.bidSize() : existing.bidSize(),
											incoming.askPrice() != null ? incoming.askPrice() : existing.askPrice(),
											incoming.askSize() != null ? incoming.askSize() : existing.askSize(),
											incoming.timestamp()
							);
						}
		);
	}

	private void updateFundingRatePatch(FundingRatePatch patch) {
		latestFundingRatePatches.merge(
						patch.coin(), patch, (existing, incoming) -> {
							return new FundingRatePatch(
											incoming.coin(),
											incoming.rate() != null ? incoming.rate() : existing.rate(),
											incoming.settlement() != null ? incoming.settlement() : existing.settlement(),
											incoming.timestamp()
							);
						}
		);
	}

	private void updateMarkPricePatch(MarkPricePatch patch) {
		latestMarkPricePatches.put(patch.coin(), patch);
	}

	private void subscribeToStreams() {
		publicWsClient().subscribeFuturesBookTicker(
						COINS, (patch) -> {
							updateBookTickerPatch(patch);
							bookTickerMessageCounts.merge(patch.coin(), 1, Integer::sum);
							checkMessages();
						}
		);

		publicWsClient().subscribeFuturesFundingRates(
						COINS, (patch) -> {
							updateFundingRatePatch(patch);
							fundingRateMessageCounts.merge(patch.coin(), 1, Integer::sum);
							checkMessages();
						}
		);

		publicWsClient().subscribeFuturesMarkPrice(
						COINS, (patch) -> {
							updateMarkPricePatch(patch);
							markPriceMessageCounts.merge(patch.coin(), 1, Integer::sum);
							checkMessages();
						}
		);
	}

	@Test
	@Tag("websocket")
	public void testPublicWsClientSubscriptions() throws Exception {
		publicWsClient().connect().join();
		initializeMessageCounts();
		subscribeToStreams();

		waitingFuture = new CompletableFuture<>();
		waitingFuture.completeOnTimeout(null, WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
		waitingFuture.get();

		boolean allStreamsReceived = true;
		boolean allFieldsPresent = true;

		for (String coin : COINS) {
			Integer bookTickerCount = bookTickerMessageCounts.get(coin);
			Integer fundingRateCount = fundingRateMessageCounts.get(coin);
			Integer markPriceCount = markPriceMessageCounts.get(coin);
			BookTickerPatch bookTickerPatch = latestBookTickerPatches.get(coin);
			FundingRatePatch fundingRatePatch = latestFundingRatePatches.get(coin);
			MarkPricePatch markPricePatch = latestMarkPricePatches.get(coin);

			if (NumberUtils.min(
							bookTickerCount == null ? 0 : bookTickerCount,
							fundingRateCount == null ? 0 : fundingRateCount,
							markPriceCount == null ? 0 : markPriceCount
			) < MIN_MESSAGES_PER_STREAM) {
				allStreamsReceived = false;
				break;
			}

			if (bookTickerPatch == null ||
					bookTickerPatch.bidPrice() == null ||
					bookTickerPatch.bidSize() == null ||
					bookTickerPatch.askPrice() == null ||
					bookTickerPatch.askSize() == null ||
					fundingRatePatch == null ||
					fundingRatePatch.rate() == null ||
					fundingRatePatch.settlement() == null ||
					markPricePatch == null) {
				allFieldsPresent = false;
				break;
			}
		}

		if (!allStreamsReceived) {
			Logger.error("Did not receive minimum messages for all streams within timeout. Counts: ");
		} else if (!allFieldsPresent) {
			Logger.error("Did not receive complete patch fields for all streams within timeout.");
		} else {
			publicWsClient().close();
			return;
		}

		Logger.log("Book Ticker: ");
		Logger.logCoinVector(bookTickerMessageCounts);
		Logger.logCoinVector(latestBookTickerPatches);

		Logger.log("Funding Rate: ");
		Logger.logCoinVector(fundingRateMessageCounts);
		Logger.logCoinVector(latestFundingRatePatches);

		Logger.log("Mark Price: ");
		Logger.logCoinVector(markPriceMessageCounts);
		Logger.logCoinVector(latestMarkPricePatches);
		throw new Exception("Test failed due to insufficient messages or incomplete patch data. See logs for details.");
	}
}
