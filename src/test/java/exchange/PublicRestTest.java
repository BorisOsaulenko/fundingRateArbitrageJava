package exchange;

import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public abstract class PublicRestTest {
	private static final long REQUEST_TIMEOUT_SECONDS = 8;
	private static final double MAX_ABS_FUNDING_RATE = 0.05;
	private static final Duration MAX_FUNDING_SETTLEMENT_AHEAD = Duration.ofHours(8);
	private final List<String> coins = List.of("SOL", "ETH", "XRP", "LTC", "ADA");

	private static <T> T getWithTimeout(CompletableFuture<T> future) throws Exception {
		return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static void assertFinite(double value, String message) {
		assertTrue(Double.isFinite(value), message);
	}

	protected abstract PublicHttpClient publicRest();

	void validateLotSize(double lotSize) throws Exception {
		assertFinite(lotSize, "Lot size should be finite");
		assertTrue(lotSize > 0, "Lot size should be greater than 0");
	}

	void validateBookTicker(BookTicker ticker) throws Exception {
		assertNotNull(ticker, "Book ticker should not be null");
		assertFinite(ticker.askPrice, "Ask price should be finite");
		assertFinite(ticker.askSize, "Ask volume should be finite");
		assertFinite(ticker.bidPrice, "Bid price should be finite");
		assertFinite(ticker.bidSize, "Bid volume should be finite");
		assertTrue(ticker.askPrice > 0, "Ask price should be greater than 0");
		assertTrue(ticker.askSize > 0, "Ask volume should be greater than 0");
		assertTrue(ticker.bidPrice > 0, "Bid price should be greater than 0");
		assertTrue(ticker.bidSize > 0, "Bid volume should be greater than 0");
		assertTrue(ticker.askPrice >= ticker.bidPrice, "Ask price should be greater than or equal to bid price");
		assertTrue(ticker.askPrice - ticker.bidPrice < 5, "Spread should be less than maxSpread");
	}

	void validateFundingRate(FundingRate fundingRate) throws Exception {
		assertNotNull(fundingRate, "Funding rate should not be null");
		assertNotNull(fundingRate.settlement, "Settlement time should not be null");
		assertFinite(fundingRate.rate, "Funding rate should be finite");
		assertTrue(
						Math.abs(fundingRate.rate) < MAX_ABS_FUNDING_RATE,
						"Funding rate absolute value should be less than " + MAX_ABS_FUNDING_RATE
		);
		Instant now = Instant.now();
		assertTrue(fundingRate.settlement.compareTo(now) > 0, "Settlement time should be in the future");
		assertTrue(
						Duration.between(now, fundingRate.settlement).compareTo(MAX_FUNDING_SETTLEMENT_AHEAD) <= 0,
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

	void validateTradingVolume(double volume) throws Exception {
		assertFinite(volume, "24h trading volume should be finite");
		assertTrue(volume >= 0, "24h trading volume should be not less than 0");
	}

	void validateFundingInterval(int fundingIntervalHours) throws Exception {
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
}
