package exchange;

import com.boris.fundingarbitrage.exchange.publicws.FuturesHandler;
import com.boris.fundingarbitrage.exchange.publicws.PublicWsClient;
import com.boris.fundingarbitrage.exchange.publicws.SpotHandler;
import com.boris.fundingarbitrage.model.websocket.patch.BookTickerPatch;
import com.boris.fundingarbitrage.model.websocket.patch.FundingPatch;
import com.boris.fundingarbitrage.model.websocket.patch.GenericPublicWsPatch;
import com.boris.fundingarbitrage.model.websocket.patch.MarkPatch;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import com.boris.fundingarbitrage.util.logger.CoinVectorLogger;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Tag("integration")
public abstract class PublicWsTest {
	private static final Logger log = LoggerFactory.getLogger(PublicWsTest.class);
	private static final Set<String> COINS = Set.of(
					"PARTI",
					"USTC",
					"ZBT",
					"SOL"
	);

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(20);
	private static final int MIN_MESSAGES_PER_STREAM = 3;
	private final CoinVector<Integer> bookTickerMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> spotBookTickerMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> fundingRateMessageCounts = new CoinVector<>();
	private final CoinVector<Integer> markPriceMessageCounts = new CoinVector<>();
	private final CoinVector<BookTickerPatch> latestBookTickerPatches = new CoinVector<>();
	private final CoinVector<BookTickerPatch> latestSpotBookTickerPatches = new CoinVector<>();
	private final CoinVector<FundingPatch> latestFundingRatePatches = new CoinVector<>();
	private final CoinVector<MarkPatch> latestMarkPricePatches = new CoinVector<>();

	private CompletableFuture<Void> waitingFuture;

	protected abstract PublicWsClient publicWsClient();

	private void initializeMessageCounts() {
		for (String coin : COINS) {
			bookTickerMessageCounts.put(coin, 0);
			spotBookTickerMessageCounts.put(coin, 0);
			fundingRateMessageCounts.put(coin, 0);
			markPriceMessageCounts.put(coin, 0);
		}
	}

	private void checkMessages() {
		if (bookTickerMessageCounts.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM).isEmpty() &&
				spotBookTickerMessageCounts.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM).isEmpty() &&
				fundingRateMessageCounts.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM).isEmpty() &&
				markPriceMessageCounts.filter((Integer value) -> value < MIN_MESSAGES_PER_STREAM).isEmpty()) {
			this.waitingFuture.complete(null);
		}
	}

	private void updateBookTickerPatch(BookTickerPatch patch) {
		latestBookTickerPatches.merge(
						patch.coin(), patch, (existing, incoming) -> new BookTickerPatch(
										incoming.coin(),
										incoming.bidPrice() != null ? incoming.bidPrice() : existing.bidPrice(),
										incoming.bidSize() != null ? incoming.bidSize() : existing.bidSize(),
										incoming.askPrice() != null ? incoming.askPrice() : existing.askPrice(),
										incoming.askSize() != null ? incoming.askSize() : existing.askSize(),
										incoming.timestamp()
						)
		);
	}

	private void updateSpotBookTickerPatch(BookTickerPatch patch) {
		latestSpotBookTickerPatches.merge(
						patch.coin(), patch, (existing, incoming) -> new BookTickerPatch(
										incoming.coin(),
										incoming.bidPrice() != null ? incoming.bidPrice() : existing.bidPrice(),
										incoming.bidSize() != null ? incoming.bidSize() : existing.bidSize(),
										incoming.askPrice() != null ? incoming.askPrice() : existing.askPrice(),
										incoming.askSize() != null ? incoming.askSize() : existing.askSize(),
										incoming.timestamp()
						)
		);
	}

	private void updateFundingRatePatch(FundingPatch patch) {
		latestFundingRatePatches.merge(
						patch.coin(), patch, (existing, incoming) -> new FundingPatch(
										incoming.coin(),
										incoming.rate() != null ? incoming.rate() : existing.rate(),
										incoming.settlement() != null ? incoming.settlement() : existing.settlement(),
										incoming.timestamp()
						)
		);
	}

	private void updateMarkPricePatch(MarkPatch patch) {
		latestMarkPricePatches.put(patch.coin(), patch);
	}

	private <T extends GenericPublicWsPatch> Consumer<T> createHandler(
					Consumer<T> patchConsumer,
					CoinVector<Integer> messageCounts
	) {
		return (T patch) -> {
			patchConsumer.accept(patch);
			messageCounts.merge(patch.coin(), 1, Integer::sum);
			checkMessages();
		};
	}

	private void subscribeToStreams() {
		FuturesHandler futuresHandler = new FuturesHandler(
						createHandler(this::updateBookTickerPatch, bookTickerMessageCounts),
						createHandler(this::updateMarkPricePatch, markPriceMessageCounts),
						createHandler(this::updateFundingRatePatch, fundingRateMessageCounts)
		);

		SpotHandler spotHandler = new SpotHandler(createHandler(
						this::updateSpotBookTickerPatch,
						spotBookTickerMessageCounts
		));

		publicWsClient().subscribeFutures(COINS, futuresHandler);
		publicWsClient().subscribeSpot(COINS, spotHandler);
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
			Integer spotBookTickerCount = spotBookTickerMessageCounts.get(coin);
			Integer fundingRateCount = fundingRateMessageCounts.get(coin);
			Integer markPriceCount = markPriceMessageCounts.get(coin);
			BookTickerPatch bookTickerPatch = latestBookTickerPatches.get(coin);
			BookTickerPatch spotBookTickerPatch = latestSpotBookTickerPatches.get(coin);
			FundingPatch fundingRatePatch = latestFundingRatePatches.get(coin);
			MarkPatch markPricePatch = latestMarkPricePatches.get(coin);

			if (NumberUtils.min(
							bookTickerCount == null ? 0 : bookTickerCount,
							spotBookTickerCount == null ? 0 : spotBookTickerCount,
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
					spotBookTickerPatch == null ||
					spotBookTickerPatch.bidPrice() == null ||
					spotBookTickerPatch.bidSize() == null ||
					spotBookTickerPatch.askPrice() == null ||
					spotBookTickerPatch.askSize() == null ||
					fundingRatePatch == null ||
					fundingRatePatch.rate() == null ||
					fundingRatePatch.settlement() == null ||
					markPricePatch == null) {
				allFieldsPresent = false;
				break;
			}
		}

		if (!allStreamsReceived) {
			log.error("Did not receive minimum messages for all streams within timeout. Counts: ");
		} else if (!allFieldsPresent) {
			log.error("Did not receive complete patch fields for all streams within timeout.");
		} else {
			publicWsClient().close();
			return;
		}

		log.info("Book Ticker: ");
		CoinVectorLogger.logCoinVector(log, bookTickerMessageCounts);
		CoinVectorLogger.logCoinVector(log, latestBookTickerPatches);

		log.info("Spot Book Ticker: ");
		CoinVectorLogger.logCoinVector(log, spotBookTickerMessageCounts);
		CoinVectorLogger.logCoinVector(log, latestSpotBookTickerPatches);

		log.info("Funding Rate: ");
		CoinVectorLogger.logCoinVector(log, fundingRateMessageCounts);
		CoinVectorLogger.logCoinVector(log, latestFundingRatePatches);

		log.info("Mark Price: ");
		CoinVectorLogger.logCoinVector(log, markPriceMessageCounts);
		CoinVectorLogger.logCoinVector(log, latestMarkPricePatches);
		throw new Exception("Test failed due to insufficient messages or incomplete patch data. See logs for details.");
	}
}
