package exchange;

import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.exchange.publichttp.PublicOnePullData;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public abstract class PublicRestTest {
	private static final long REQUEST_TIMEOUT_SECONDS = 8;
	private static final BigDecimal MAX_ABS_FUNDING_RATE = new BigDecimal("0.05");
	private static final Duration MAX_FUNDING_SETTLEMENT_AHEAD = Duration.ofHours(8);
	private final Set<String> coins = Set.of("SOL", "ETH", "XRP", "LTC", "ADA");

	private static <T> T getWithTimeout(CompletableFuture<T> future) throws Exception {
		return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static void assertFinite(double value, String message) {
		assertTrue(Double.isFinite(value), message);
	}

	protected abstract PublicHttpClient publicRest();

	void validateLotSize(BigDecimal lotSize) {
		assertTrue(lotSize.compareTo(BigDecimal.ZERO) > 0, "Lot size should be greater than 0");
	}

	void validateBookTicker(BookTicker ticker) {
		assertNotNull(ticker, "Book ticker should not be null");
		assertTrue(ticker.askPrice().compareTo(BigDecimal.ZERO) > 0, "Ask price should be greater than 0");
		assertTrue(ticker.askSize().compareTo(BigDecimal.ZERO) > 0, "Ask volume should be greater than 0");
		assertTrue(ticker.bidPrice().compareTo(BigDecimal.ZERO) > 0, "Bid price should be greater than 0");
		assertTrue(ticker.bidSize().compareTo(BigDecimal.ZERO) > 0, "Bid volume should be greater than 0");

		boolean askGreaterThanBid = ticker.askPrice().compareTo(ticker.bidPrice()) >= 0;
		assertTrue(askGreaterThanBid, "Ask price should be greater than or equal to bid price");

		boolean askAndBidNotTooFarAway = ticker.askPrice().subtract(ticker.bidPrice()).compareTo(BigDecimal.valueOf(5)) < 0;
		assertTrue(askAndBidNotTooFarAway, "Spread should be less than maxSpread");
	}

	void validateFundingRate(FundingRate fundingRate) {
		assertNotNull(fundingRate, "Funding rate should not be null");
		assertNotNull(fundingRate.settlement(), "Settlement time should not be null");
		assertTrue(
						fundingRate.rate().abs().compareTo(MAX_ABS_FUNDING_RATE) < 0,
						"Funding rate absolute value should be less than " + MAX_ABS_FUNDING_RATE
		);
		Instant now = Instant.now();
		assertTrue(fundingRate.settlement().compareTo(now) > 0, "Settlement time should be in the future");
		assertTrue(
						Duration.between(now, fundingRate.settlement()).compareTo(MAX_FUNDING_SETTLEMENT_AHEAD) <= 0,
						"Settlement time should be within " + MAX_FUNDING_SETTLEMENT_AHEAD.toHours() + " hours"
		);
	}

	@Test
	@Tag("rest")
	void getFundingRatesBatch() throws Exception {
		Map<String, FundingRate> fundingRates = getWithTimeout(publicRest().getFundingRate(coins));
		assertNotNull(fundingRates, "Funding rates map should not be null");
		assertEquals(coins.size(), fundingRates.size(), "Funding rates should be returned for each requested symbol");
		for (FundingRate fundingRate : fundingRates.values()) {
			validateFundingRate(fundingRate);
		}
	}

	void validateTradingVolume(BigDecimal volume) {
		assertTrue(
						volume.compareTo(BigDecimal.valueOf(10_000)) > 0,
						"24h trading volume should be not less than 10 000"
		); // testing on popular coins, so we can expect some volume
	}

	void validateFundingInterval(int fundingIntervalHours) {
		assertTrue(fundingIntervalHours > 0, "Funding granularity should be greater than 0");
		assertTrue(fundingIntervalHours <= 8, "Funding granularity should be less than or equal to 24 hours");
		assertEquals(0, fundingIntervalHours % 2, "Funding granularity should be even");
	}

	@Test
	@Tag("rest")
	void publicOnePullData() throws Exception {
		var result = getWithTimeout(publicRest().getOnePullData(coins));
		assertNotNull(result, "One pull data should not be null");
		assertEquals(result.size(), coins.size(), "One pull data should contain data for each requested symbol");
		for (String coin : coins) {
			var data = result.get(coin);
			assertNotNull(data, "One pull data for " + coin + " should not be null");
			validateLotSize(data.lotSize());
			validateBookTicker(data.bookTicker());
			validateTradingVolume(data.volume24h());
			validateFundingInterval(data.fundingInterval());
		}
	}

	@Test
	@Tag("rest")
	void publicOnePullDataReturnNullOnNonExistentSymbol() throws Exception {
		var result = getWithTimeout(publicRest().getOnePullData(Set.of("NONEXISTENT")));
		assertNotNull(result, "One pull data should be null for non-existent symbol");
		assertEquals(1, result.size(), "One pull data should contain data for each requested symbol");
		Map.Entry<String, PublicOnePullData> entry = result.entrySet().iterator().next();
		assertTrue(entry.getValue().isEmpty(), "One pull data for " + entry.getKey() + " should be empty");
		assertEquals("NONEXISTENT", entry.getKey());
	}
}
