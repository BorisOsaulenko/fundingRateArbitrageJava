package strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public abstract class PreTradeArbitrageStrategyTest {
	private static ArbitrageSnapshot snapshot(
					BigDecimal longAsk,
					BigDecimal shortBid,
					BigDecimal longFundingRate,
					BigDecimal shortFundingRate,
					BigDecimal longMarkPrice,
					BigDecimal shortMarkPrice
	) {
		BigDecimal delta = BigDecimal.valueOf(0.1);
		BigDecimal feeEntry = BigDecimal.valueOf(0.0002);
		Instant now = Instant.now();
		BookTicker longBook = new BookTicker(longAsk.subtract(delta), BigDecimal.TEN, longAsk, BigDecimal.TEN, now);
		BookTicker shortBook = new BookTicker(shortBid, BigDecimal.TEN, shortBid.add(delta), BigDecimal.TEN, now);
		Fees fees = new Fees(feeEntry, feeEntry, feeEntry, feeEntry, now);
		FundingRate longFunding = new FundingRate(longFundingRate, now.plusSeconds(3600), now);
		FundingRate shortFunding = new FundingRate(shortFundingRate, now.plusSeconds(3600), now);
		MarkPrice longMark = markPrice(longMarkPrice, now);
		MarkPrice shortMark = markPrice(shortMarkPrice, now);
		ExchangeSnapshot longExchange = new ExchangeSnapshot(longBook, fees, longFunding, longMark);
		ExchangeSnapshot shortExchange = new ExchangeSnapshot(shortBook, fees, shortFunding, shortMark);
		return new ArbitrageSnapshot(longExchange, shortExchange);
	}

	private static MarkPrice markPrice(BigDecimal price, Instant timestamp) {
		return new MarkPrice(price, timestamp);
	}

	protected abstract PreTradeStrategy strategy();

	@Test
	public void compareSnapshotsReturnsMinusOneWhenFirstIsWorse() {
		ArbitrageSnapshot worse = snapshot(
						BigDecimal.valueOf(102),
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(0.001),
						BigDecimal.valueOf(0.0005),
						BigDecimal.valueOf(101),
						BigDecimal.valueOf(99)
		);
		ArbitrageSnapshot better = snapshot(
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102),
						BigDecimal.valueOf(0.001),
						BigDecimal.valueOf(0.002),
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102)
		);
		assertEquals(BigDecimal.ONE.negate().intValueExact(), strategy().compareSnapshots(worse, better));
	}

	@Test
	public void compareSnapshotsReturnsZeroWhenEquivalent() {
		ArbitrageSnapshot first = snapshot(
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102)
		);
		ArbitrageSnapshot second = snapshot(
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102)
		);
		assertEquals(BigDecimal.ZERO.intValueExact(), strategy().compareSnapshots(first, second));
	}

	@Test
	public void compareSnapshotsReturnsOneWhenFirstIsBetter() {
		ArbitrageSnapshot better = snapshot(
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102)
		);
		ArbitrageSnapshot worse = snapshot(
						BigDecimal.valueOf(101),
						BigDecimal.valueOf(100),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(101),
						BigDecimal.valueOf(100)
		);
		assertEquals(BigDecimal.ONE.intValueExact(), strategy().compareSnapshots(better, worse));
	}

	@Test
	public void snapshotGoodEnoughReturnsFalseForNegativeGain() {
		ArbitrageSnapshot negativeGain = snapshot(
						BigDecimal.valueOf(102),
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(0.001),
						BigDecimal.valueOf(0.0005),
						BigDecimal.valueOf(101),
						BigDecimal.valueOf(99)
		);
		assertFalse(strategy().snapshotGoodEnough(negativeGain));
	}
}
