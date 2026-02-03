package exchange;

import com.boris.fundingarbitrage.exchange.ExchangeContext;
import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.privatehttp.PrivateHttpClient;
import com.boris.fundingarbitrage.model.assetops.MarginMode;
import com.boris.fundingarbitrage.model.assetops.SupportedChain;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.exchange.ExchangeChains;
import com.boris.fundingarbitrage.model.exchange.WalletAddress;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public abstract class PrivateRestTest {
	private final String testCoin = "SOL";
	private final Duration timestampTolerance = Duration.ofSeconds(2);
	private final ExchangeContext context = new BinanceContext();

	protected abstract PrivateHttpClient privateRest();

	@Test
	public void getTradingFees() throws Exception {
		Fees fees = privateRest().getTradingFees(testCoin).get();
		assertTrue(fees.openTaker() >= 0, "Open taker fee should be non-negative");
		assertTrue(fees.closeTaker() >= 0, "Open maker fee should be non-negative");

		Instant now = Instant.now();
		assertTrue(
						Duration.between(fees.timestamp(), now).compareTo(timestampTolerance) < 0,
						"Fees timestamp should be recent. Difference: " + Duration
										.between(fees.timestamp(), now)
										.toMillis() + " ms"
		);
	}

	@Test
	public void getSpotUsdtBalance() throws Exception {
		double spotBalance = privateRest().getSpotUsdtBalance().get();
		assertTrue(spotBalance >= 0, "Spot USDT balance should be non-negative");
	}

	@Test
	public void getFuturesUsdtBalance() throws Exception {
		double futuresBalance = privateRest().getFuturesUsdtBalance().get();
		assertTrue(futuresBalance >= 0, "Futures USDT balance should be non-negative");
	}

	@Test
	public void changeLeverage() throws Exception {
		int leverage = 2;
		assertTimeout(
						Duration.ofSeconds(5),
						() -> privateRest().changeLeverage(testCoin, leverage).get(),
						"Changing leverage should not throw an exception"
		);
	}

	@Test
	public void setMarginMode() throws Exception {
		MarginMode marginMode = MarginMode.ISOLATED;
		assertTimeout(
						Duration.ofSeconds(5),
						() -> privateRest().setMarginMode(testCoin, marginMode).get(),
						"Setting margin mode should not throw an exception"
		);
	}

	@Test
	public void getMaxLeverage() throws Exception {
		int maxLeverage = privateRest().getMaxLeverage(testCoin).get();
		assertTrue(maxLeverage >= 1, "Max leverage should be at least 1");
	}

	@Test
	public void getSupportedChains() throws Exception {
		ExchangeChains chains = privateRest().getSupportedChains().get();
		assertFalse(
						chains.depositableChains().isEmpty(),
						"There should be at least one depositable chain"
		);
		assertFalse(
						chains.withdrawableChains().isEmpty(),
						"There should be at least one withdrawable chain"
		);
	}

	@Test
	public void getUsdtWalletAddress() throws Exception {
		WalletAddress address = privateRest()
						.getUsdtWalletAddress(SupportedChain.ERC)
						.get(); // ERC is everywhere
		Logger.getInstance().log(address.address());
		assertNotNull(address, "Wallet address should not be null");
		assertEquals(
						SupportedChain.ERC,
						address.chain(),
						"Wallet address chain should match requested chain"
		);
		assertNotNull(address.address(), "Wallet address should not be null");
		assertTrue(address.address().length() > 15, "Wallet address should have a valid length");
	}
}
