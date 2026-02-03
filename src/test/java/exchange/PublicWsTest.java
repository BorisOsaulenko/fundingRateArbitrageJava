package exchange;

import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

@Tag("integration")
public abstract class PublicWsTest {
	private static final String[] COINS = {"SOL"};
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
	private static final int MIN_MESSAGES_PER_STREAM = 3;
	private final CoinVector<Integer> bookTickerMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> fundingRateMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> markPriceMessageCounts = new CoinVector<>();

	protected abstract PublicWsClient publicWsClient();

	private void initializeMessageCounts() {
		for (String coin : COINS) {
			bookTickerMessageCounts.put(coin, 0);
			fundingRateMessageCounts.put(coin, 0);
			markPriceMessageCounts.put(coin, 0);
		}
	}

	private void subscribeToStreams() {
		publicWsClient().subscribeBookTicker(
						COINS,
						(patch) -> bookTickerMessageCounts.merge(patch.coin(), 1, Integer::sum)

		);

		publicWsClient().subscribeFundingRates(
						COINS,
						(patch) -> fundingRateMessageCounts.merge(patch.coin(), 1, Integer::sum)

		);

		publicWsClient().subscribeMarkPrice(
						COINS,
						(patch) -> markPriceMessageCounts.merge(patch.coin(), 1, Integer::sum)

		);
	}

	@Test
	public void testPublicWsClientSubscriptions() throws Exception {
		initializeMessageCounts();
		subscribeToStreams();
		Thread.sleep(WAIT_TIMEOUT.toMillis());

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
