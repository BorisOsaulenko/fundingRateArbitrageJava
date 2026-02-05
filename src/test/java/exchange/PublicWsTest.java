package exchange;

import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Tag("integration")
public abstract class PublicWsTest<T extends PublicMessageHandler> {
	private static final String[] COINS = {"SOL"};
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(180);
	private static final int MIN_MESSAGES_PER_STREAM = 3;
	private final CoinVector<Integer> bookTickerMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> fundingRateMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> markPriceMessageCounts = new CoinVector<>();
	private final CoinVector<BookTickerPatch> latestBookTickerPatches = new CoinVector<>();
	private final CoinVector<FundingRatePatch> latestFundingRatePatches = new CoinVector<>();
	private final CoinVector<MarkPricePatch> latestMarkPricePatches = new CoinVector<>();

	private CompletableFuture<Void> waitingFuture;

	protected abstract PublicWsClient<T> publicWsClient();

	private void initializeMessageCounts() {
		for (String coin : COINS) {
			bookTickerMessageCounts.put(coin, 0);
			fundingRateMessageCounts.put(coin, 0);
			markPriceMessageCounts.put(coin, 0);
		}
	}

	private void checkMessages() {
		if (bookTickerMessageCounts
						.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM)
						.isEmpty() && fundingRateMessageCounts
						.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM)
						.isEmpty() && markPriceMessageCounts
						.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM)
						.isEmpty()) {
			this.waitingFuture.complete(null);
		}
	}

	private void updateBookTickerPatch(BookTickerPatch patch) {
		latestBookTickerPatches.merge(
						patch.coin(), patch, (existing, incoming) -> {
							if (existing == null) return incoming;
							return new BookTickerPatch(
											incoming.coin(),
											incoming.bid() != null ? incoming.bid() : existing.bid(),
											incoming.ask() != null ? incoming.ask() : existing.ask(),
											incoming.timestamp()
							);
						}
		);
	}

	private void updateFundingRatePatch(FundingRatePatch patch) {
		latestFundingRatePatches.merge(
						patch.coin(), patch, (existing, incoming) -> {
							if (existing == null) return incoming;
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
		publicWsClient().subscribeBookTicker(
						COINS, (patch) -> {
							updateBookTickerPatch(patch);
							bookTickerMessageCounts.merge(patch.coin(), 1, Integer::sum);
							checkMessages();
						}
		);

		publicWsClient().subscribeFundingRates(
						COINS, (patch) -> {
							updateFundingRatePatch(patch);
							fundingRateMessageCounts.merge(patch.coin(), 1, Integer::sum);
							checkMessages();
						}
		);

		publicWsClient().subscribeMarkPrice(
						COINS, (patch) -> {
							updateMarkPricePatch(patch);
							markPriceMessageCounts.merge(patch.coin(), 1, Integer::sum);
							checkMessages();
						}
		);
	}

	@Test
	public void testPublicWsClientSubscriptions() throws Exception {
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

			if (bookTickerPatch == null || bookTickerPatch.bid() == null || bookTickerPatch.ask() == null || fundingRatePatch == null || fundingRatePatch.rate() == null || fundingRatePatch.settlement() == null || markPricePatch == null) {
				allFieldsPresent = false;
				break;
			}
		}

		if (!allStreamsReceived) {
			Logger
							.getInstance()
							.error("Did not receive minimum messages for all streams within timeout. Counts: ");
		} else if (!allFieldsPresent) {
			Logger
							.getInstance()
							.error("Did not receive complete patch fields for all streams within timeout.");
		} else {
			publicWsClient().close();
			return;
		}

		Logger.getInstance().log("Book Ticker: ");
		Logger.getInstance().logCoinVector(bookTickerMessageCounts);
		Logger.getInstance().logCoinVector(latestBookTickerPatches);

		Logger.getInstance().log("Funding Rate: ");
		Logger.getInstance().logCoinVector(fundingRateMessageCounts);
		Logger.getInstance().logCoinVector(latestFundingRatePatches);

		Logger.getInstance().log("Mark Price: ");
		Logger.getInstance().logCoinVector(markPriceMessageCounts);
		Logger.getInstance().logCoinVector(latestMarkPricePatches);
	}
}
