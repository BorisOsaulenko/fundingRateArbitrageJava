package strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.strategy.ArbitrageStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public abstract class ArbitrageStrategyTest {
	private static ArbitrageSnapshot snapshot(
					double longAsk,
					double shortBid,
					double longFundingRate,
					double shortFundingRate,
					double longMarkPrice,
					double shortMarkPrice
	) {
		Instant now = Instant.now();
		BookTicker longBook = new BookTicker(longAsk - 0.1, 10, longAsk, 10, now);
		BookTicker shortBook = new BookTicker(shortBid, 10, shortBid + 0.1, 10, now);
		Fees fees = new Fees(0.0002, 0.0002, 0.0002, 0.0002, now);
		FundingRate longFunding = new FundingRate(longFundingRate, now.plusSeconds(3600), now);
		FundingRate shortFunding = new FundingRate(shortFundingRate, now.plusSeconds(3600), now);
		MarkPrice longMark = markPrice(longMarkPrice, now);
		MarkPrice shortMark = markPrice(shortMarkPrice, now);
		ExchangeSnapshot longExchange = new ExchangeSnapshot(longBook, fees, longFunding, longMark, BigDecimal.ONE);
		ExchangeSnapshot shortExchange = new ExchangeSnapshot(shortBook, fees, shortFunding, shortMark, BigDecimal.ONE);
		return new ArbitrageSnapshot(longExchange, shortExchange);
	}

	private static MarkPrice markPrice(double price, Instant timestamp) {
		MarkPrice markPrice = MarkPrice.empty();
		markPrice.price = price;
		markPrice.timestamp = timestamp;
		return markPrice;
	}

	protected abstract ArbitrageStrategy strategy();

	@Test
	public void compareSnapshotsReturnsMinusOneWhenFirstIsWorse() {
		ArbitrageSnapshot worse = snapshot(102, 100, 0.001, 0.0005, 101, 99);
		ArbitrageSnapshot better = snapshot(100, 102, 0.001, 0.002, 100, 102);
		assertEquals(-1, strategy().compareSnapshots(worse, better));
	}

	@Test
	public void compareSnapshotsReturnsZeroWhenEquivalent() {
		ArbitrageSnapshot first = snapshot(100, 102, 0.0, 0.0, 100, 102);
		ArbitrageSnapshot second = snapshot(100, 102, 0.0, 0.0, 100, 102);
		assertEquals(0, strategy().compareSnapshots(first, second));
	}

	@Test
	public void compareSnapshotsReturnsOneWhenFirstIsBetter() {
		ArbitrageSnapshot better = snapshot(100, 102, 0.0, 0.0, 100, 102);
		ArbitrageSnapshot worse = snapshot(101, 100, 0.0, 0.0, 101, 100);
		assertEquals(1, strategy().compareSnapshots(better, worse));
	}

	@Test
	public void snapshotGoodEnoughReturnsFalseForNegativeGain() {
		ArbitrageSnapshot negativeGain = snapshot(102, 100, 0.001, 0.0005, 101, 99);
		assertFalse(strategy().snapshotGoodEnough(negativeGain));
	}
}
