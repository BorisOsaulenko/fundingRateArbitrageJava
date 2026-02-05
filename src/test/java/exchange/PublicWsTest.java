package exchange;

import com.boris.fundingarbitrage.exchange.publicws.PublicMessageHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
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

	private void subscribeToStreams() {
		publicWsClient().subscribeBookTicker(
						COINS, (patch) -> {
							bookTickerMessageCounts.merge(patch.coin(), 1, Integer::sum);
							checkMessages();
						}
		);

		publicWsClient().subscribeFundingRates(
						COINS, (patch) -> {
							fundingRateMessageCounts.merge(patch.coin(), 1, Integer::sum);
							checkMessages();
						}
		);

		publicWsClient().subscribeMarkPrice(
						COINS, (patch) -> {
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

		for (String coin : COINS) {
			Integer bookTickerCount = bookTickerMessageCounts.get(coin);
			Integer fundingRateCount = fundingRateMessageCounts.get(coin);
			Integer markPriceCount = markPriceMessageCounts.get(coin);

			if (NumberUtils.min(
							bookTickerCount == null ? 0 : bookTickerCount,
							fundingRateCount == null ? 0 : fundingRateCount,
							markPriceCount == null ? 0 : markPriceCount
			) < MIN_MESSAGES_PER_STREAM) {
				allStreamsReceived = false;
				break;
			}
		}

		if (!allStreamsReceived) {
			Logger
							.getInstance()
							.error("Did not receive minimum messages for all streams within timeout. Counts: ");

			Logger.getInstance().log("Book Ticker: ");
			Logger.getInstance().logCoinVector(bookTickerMessageCounts);

			Logger.getInstance().log("Funding Rate: ");
			Logger.getInstance().logCoinVector(fundingRateMessageCounts);

			Logger.getInstance().log("Mark Price: ");
			Logger.getInstance().logCoinVector(markPriceMessageCounts);

			throw new AssertionError("Did not receive minimum messages for all streams within timeout");
		}

		publicWsClient().close();
	}

}
