package exchange;

import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.model.assetops.MarginMode;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.util.coinvector.CoinVector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public abstract class PrivateRestTest {
	private static final long REQUEST_TIMEOUT_SECONDS = 8;
	private static final double MAX_ABS_FEE = 0.02;
	private final String testCoin = "SOL";
	private final Set<String> testCoins = Set.of("SOL", "ADA", "ETH");
	private final Duration timestampTolerance = Duration.ofSeconds(2);

	private static <T> T getWithTimeout(CompletableFuture<T> future) throws Exception {
		return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static void assertFinite(double value, String message) {
		assertTrue(Double.isFinite(value), message);
	}

	protected abstract PrivateHttpClient privateRest();

	private void assertValidFees(Fees fees) {
		assertNotNull(fees, "Fees should not be null");
		assertFinite(fees.openTaker, "Open taker fee should be finite");
		assertFinite(fees.closeTaker, "Close taker fee should be finite");
		assertTrue(fees.openTaker >= 0, "Open taker fee should be non-negative");
		assertTrue(fees.closeTaker >= 0, "Open maker fee should be non-negative");
		assertTrue(fees.openTaker < MAX_ABS_FEE, "Open taker fee should be < " + MAX_ABS_FEE);
		assertTrue(fees.closeTaker < MAX_ABS_FEE, "Close taker fee should be < " + MAX_ABS_FEE);

		Instant now = Instant.now();
		assertTrue(
						Duration.between(fees.timestamp, now).compareTo(timestampTolerance) < 0,
						"Fees timestamp should be recent. Difference: " + Duration.between(fees.timestamp, now).toMillis() + " ms"
		);
	}

	@Test
	@Tag("rest")
	public void getTradingFeesTest() throws Exception {
		CoinVector<Fees> fees = getWithTimeout(privateRest().getTradingFees(testCoins));
		assertNotNull(fees, "Fees result should not be null");
		assertEquals(testCoins.size(), fees.size(), "Fees result should contain data for each requested coin");
		for (String coin : testCoins) {
			assertValidFees(fees.get(coin));
		}
	}

	@Test
	@Tag("rest")
	public void getSpotUsdtBalanceTest() throws Exception {
		double spotBalance = getWithTimeout(privateRest().getSpotUsdtBalance());
		assertFinite(spotBalance, "Spot USDT balance should be finite");
		assertTrue(spotBalance > 0, "Spot USDT balance should be non-negative");
	}

	@Tag("rest")
	@Test
	public void changeLeverageTest() {
		int leverage = 2;
		assertTimeout(
						Duration.ofSeconds(5),
						() -> privateRest().changeLeverage(testCoin, leverage).get(),
						"Changing leverage should not throw an exception"
		);
	}

	@Test
	@Tag("rest")
	public void getFuturesUsdtBalanceTest() throws Exception {
		double futuresBalance = getWithTimeout(privateRest().getFuturesUsdtBalance());
		assertFinite(futuresBalance, "Futures USDT balance should be finite");
		assertTrue(futuresBalance > 0, "Futures USDT balance should be non-negative");
	}

	@Test
	@Tag("rest")
	public void setMarginModeTest() {
		MarginMode marginMode = MarginMode.CROSS;
		assertTimeout(
						Duration.ofSeconds(5),
						() -> privateRest().setMarginMode(testCoin, marginMode).get(),
						"Setting margin mode should not throw an exception"
		);
	}

	@Test
	@Tag("rest")
	public void getMaxLeverageTest() throws Exception {
		Map<String, Integer> maxLeverage = getWithTimeout(privateRest().getMaxLeverage(testCoins));
		assertNotNull(maxLeverage, "Max leverage map should not be null");
		for (String coin : testCoins) {
			assertNotNull(maxLeverage.get(coin), "Max leverage for " + coin + " should not be null");
			assertTrue(maxLeverage.get(coin) >= 2, "Max leverage for " + coin + " should be >= 2");
		}
	}

	@Tag("rest")
	@Test
	public void getUsdtWalletAddressTest() throws Exception {
		WalletAddress address = getWithTimeout(privateRest().getUsdtWalletAddress(SupportedChain.ERC)); // ERC is everywhere
		assertNotNull(address, "Wallet address should not be null");
		assertEquals(SupportedChain.ERC, address.chain(), "Wallet address chain should match requested chain");
		assertNotNull(address.address(), "Wallet address string should not be null");
		assertTrue(address.address().length() > 15, "Wallet address should have a valid length");
	}

	@Test
	@Tag("rest")
	public void getSupportedChainsTest() throws Exception {
		ExchangeChains chains = getWithTimeout(privateRest().getSupportedChains());
		assertNotNull(chains, "Exchange chains should not be null");
		assertNotNull(chains.depositableChains(), "Depositable chains should not be null");
		assertNotNull(chains.withdrawableChains(), "Withdrawable chains should not be null");
		assertFalse(chains.depositableChains().isEmpty(), "There should be at least one depositable chain");
		assertFalse(chains.withdrawableChains().isEmpty(), "There should be at least one withdrawable chain");
	}
}
