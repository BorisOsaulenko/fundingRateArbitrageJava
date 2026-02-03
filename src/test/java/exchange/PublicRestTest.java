package exchange;

import com.boris.fundingarbitrage.exchange.impl.binance.BinanceContext;
import com.boris.fundingarbitrage.exchange.publichttp.PublicHttpClient;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class PublicRestTest {
	private final BinanceContext context = new BinanceContext();
	private final String testCoin = "SOL";

	protected abstract PublicHttpClient publicRest();

	@Test
	void getLotSize() throws Exception {
		double lotSize = publicRest().getLotSize(testCoin).get();
		assertTrue(lotSize > 0, "Lot size should be greater than 0");
	}

	@Test
	void getBookTicker() throws Exception {
		BookTicker bidPrice = publicRest().getBookTicker(testCoin).get();

		assertTrue(bidPrice.ask().price() > 0, "Ask price should be greater than 0");
		assertTrue(bidPrice.ask().volume() > 0, "Ask volume should be greater than 0");
		assertTrue(bidPrice.bid().price() > 0, "Bid price should be greater than 0");
		assertTrue(bidPrice.bid().volume() > 0, "Bid volume should be greater than 0");
	}

	@Test
	void getFundingRate() throws Exception {
		FundingRate fundingRate = publicRest().getFundingRate(testCoin).get();
		assertTrue(
						fundingRate.settlement().compareTo(Instant.now()) > 0,
						"Settlement time should be in the future"
		);
	}

	@Test
	void getTradingVolume24h() throws Exception {
		double volume = publicRest().getTradingVolume24h(testCoin).get();
		assertTrue(volume > 0, "24h trading volume should be greater than 0");
	}

	@Test
	void getTradingVolume1h() throws Exception {
		double volume = publicRest().getTradingVolume1h(testCoin).get();
		assertTrue(volume > 0, "1h trading volume should be greater than 0");
	}

	@Test
	void checkCoinExists() throws Exception {
		boolean exists = publicRest().checkSymbolExists(testCoin).get();
		assertTrue(exists, "Symbol should exist on the exchange");
	}

	@Test
	void checkCoinDoesNotExist() throws Exception {
		boolean exists = publicRest().checkSymbolExists("NONEXISTENTCOIN").get();
		assertFalse(exists, "Symbol should not exist on the exchange");
	}
}
