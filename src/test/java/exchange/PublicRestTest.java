package exchange;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class PublicRestTest {
	private final BinanceContext context = new BinanceContext();
	private final String testCoin = "SOL";
	private static final long REQUEST_TIMEOUT_SECONDS = 8;
	private static final double MAX_ABS_FUNDING_RATE = 0.05;
	private static final Duration MAX_FUNDING_SETTLEMENT_AHEAD = Duration.ofHours(8);

	protected abstract PublicHttpClient publicRest();

	private static <T> T getWithTimeout(CompletableFuture<T> future) throws Exception {
		return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static void assertFinite(double value, String message) {
		assertTrue(Double.isFinite(value), message);
	}

	@Test
	void getLotSize() throws Exception {
		double lotSize = getWithTimeout(publicRest().getLotSize(testCoin));
		assertFinite(lotSize, "Lot size should be finite");
		assertTrue(lotSize > 0, "Lot size should be greater than 0");
	}

	@Test
	void getBookTicker() throws Exception {
		BookTicker bidPrice = getWithTimeout(publicRest().getBookTicker(testCoin));
		assertNotNull(bidPrice, "Book ticker should not be null");
		assertNotNull(bidPrice.ask(), "Ask should not be null");
		assertNotNull(bidPrice.bid(), "Bid should not be null");

		assertFinite(bidPrice.ask().price(), "Ask price should be finite");
		assertFinite(bidPrice.ask().volume(), "Ask volume should be finite");
		assertFinite(bidPrice.bid().price(), "Bid price should be finite");
		assertFinite(bidPrice.bid().volume(), "Bid volume should be finite");
		assertTrue(bidPrice.ask().price() > 0, "Ask price should be greater than 0");
		assertTrue(bidPrice.ask().volume() > 0, "Ask volume should be greater than 0");
		assertTrue(bidPrice.bid().price() > 0, "Bid price should be greater than 0");
		assertTrue(bidPrice.bid().volume() > 0, "Bid volume should be greater than 0");
		assertTrue(
						bidPrice.ask().price() >= bidPrice.bid().price(),
						"Ask price should be greater than or equal to bid price"
		);
	}

	@Test
	void getFundingRate() throws Exception {
		FundingRate fundingRate = getWithTimeout(publicRest().getFundingRate(testCoin));
		assertNotNull(fundingRate, "Funding rate should not be null");
		assertNotNull(fundingRate.settlement(), "Settlement time should not be null");
		assertFinite(fundingRate.rate(), "Funding rate should be finite");
		assertTrue(
						Math.abs(fundingRate.rate()) < MAX_ABS_FUNDING_RATE,
						"Funding rate absolute value should be less than " + MAX_ABS_FUNDING_RATE
		);
		Instant now = Instant.now();
		assertTrue(
						fundingRate.settlement().compareTo(now) > 0,
						"Settlement time should be in the future"
		);
		assertTrue(
						Duration.between(now, fundingRate.settlement())
										.compareTo(MAX_FUNDING_SETTLEMENT_AHEAD) <= 0,
						"Settlement time should be within " + MAX_FUNDING_SETTLEMENT_AHEAD.toHours() + " hours"
		);
	}

	@Test
	void getTradingVolume24h() throws Exception {
		double volume = getWithTimeout(publicRest().getTradingVolume24h(testCoin));
		assertFinite(volume, "24h trading volume should be finite");
		assertTrue(volume > 0, "24h trading volume should be greater than 0");
	}

	@Test
	void getTradingVolume1h() throws Exception {
		double volume = getWithTimeout(publicRest().getTradingVolume1h(testCoin));
		assertFinite(volume, "1h trading volume should be finite");
		assertTrue(volume > 0, "1h trading volume should be greater than 0");
	}

	@Test
	void checkCoinExists() throws Exception {
		boolean exists = getWithTimeout(publicRest().checkSymbolExists(testCoin));
		assertTrue(exists, "Symbol should exist on the exchange");
	}

	@Test
	void checkCoinDoesNotExist() throws Exception {
		boolean exists = getWithTimeout(publicRest().checkSymbolExists("NONEXISTENTCOIN"));
		assertFalse(exists, "Symbol should not exist on the exchange");
	}
}
