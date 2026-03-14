package strategy;

import com.boris.fundingarbitrage.model.arbitrage.ArbitrageConstantData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageData;
import com.boris.fundingarbitrage.model.arbitrage.ArbitrageSnapshot;
import com.boris.fundingarbitrage.model.contract.BookTicker;
import com.boris.fundingarbitrage.model.contract.Fees;
import com.boris.fundingarbitrage.model.contract.FundingRate;
import com.boris.fundingarbitrage.model.contract.MarkPrice;
import com.boris.fundingarbitrage.model.exchange.ExchangeConstantData;
import com.boris.fundingarbitrage.model.exchange.ExchangeSnapshot;
import com.boris.fundingarbitrage.strategy.PreTradeStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public abstract class PreTradeArbitrageStrategyTest {
	private static ArbitrageData snapshot(
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
		ExchangeSnapshot longExchange = new ExchangeSnapshot(longBook, longFunding, longMark);
		ExchangeSnapshot shortExchange = new ExchangeSnapshot(shortBook, shortFunding, shortMark);
		ExchangeConstantData longConstantData = new ExchangeConstantData(new BigDecimal("0.1"), fees, 4);
		ExchangeConstantData shortConstantData = new ExchangeConstantData(new BigDecimal("0.1"), fees, 4);
		return new ArbitrageData(
						new ArbitrageSnapshot(longExchange, shortExchange),
						new ArbitrageConstantData(longConstantData, shortConstantData)
		);
	}

	private static MarkPrice markPrice(BigDecimal price, Instant timestamp) {
		return new MarkPrice(price, timestamp);
	}

	protected abstract PreTradeStrategy strategy();

	@Test
	public void compareSnapshotsReturnsMinusOneWhenFirstIsWorse() {
		ArbitrageData worse = snapshot(
						BigDecimal.valueOf(102),
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(0.001),
						BigDecimal.valueOf(0.0005),
						BigDecimal.valueOf(101),
						BigDecimal.valueOf(99)
		);
		ArbitrageData better = snapshot(
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102),
						BigDecimal.valueOf(0.001),
						BigDecimal.valueOf(0.002),
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102)
		);
		assertEquals(-1, strategy().compareArbData(worse, better));
	}

	@Test
	public void compareSnapshotsReturnsZeroWhenEquivalent() {
		ArbitrageData first = snapshot(
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102)
		);
		ArbitrageData second = snapshot(
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102)
		);
		assertEquals(BigDecimal.ZERO.intValueExact(), strategy().compareArbData(first, second));
	}

	@Test
	public void compareSnapshotsReturnsOneWhenFirstIsBetter() {
		ArbitrageData better = snapshot(
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(102)
		);
		ArbitrageData worse = snapshot(
						BigDecimal.valueOf(101),
						BigDecimal.valueOf(100),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(101),
						BigDecimal.valueOf(100)
		);
		assertEquals(BigDecimal.ONE.intValueExact(), strategy().compareArbData(better, worse));
	}

	@Test
	public void snapshotGoodEnoughReturnsFalseForNegativeGain() {
		ArbitrageData negativeGain = snapshot(
						BigDecimal.valueOf(102),
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(0.001),
						BigDecimal.valueOf(0.0005),
						BigDecimal.valueOf(101),
						BigDecimal.valueOf(99)
		);
		assertFalse(strategy().arbDataGoodEnough(negativeGain));
	}

	@Test
	public void realDataTest1() {
		BookTicker longBook = new BookTicker(
						new BigDecimal("0.25571"),
						new BigDecimal("26"),
						new BigDecimal("0.25587"),
						new BigDecimal("204"),
						Instant.parse("2026-03-13T10:18:27.853Z")
		);
		Fees longFees = new Fees(
						new BigDecimal("0.00020"),
						new BigDecimal("0.0005"),
						new BigDecimal("0.00020"),
						new BigDecimal("0.0005"),
						Instant.parse("2026-03-13T10:09:32.159327Z")
		);
		FundingRate longFunding = new FundingRate(
						new BigDecimal("-0.010513"),
						Instant.parse("2026-03-13T12:00:00Z"),
						Instant.parse("2026-03-13T10:18:26Z")
		);
		MarkPrice longMark = new MarkPrice(
						new BigDecimal("0.25617"),
						Instant.parse("2026-03-13T10:18:26Z")
		);
		ExchangeSnapshot longExchange = new ExchangeSnapshot(longBook, longFunding, longMark);

		BookTicker shortBook = new BookTicker(
						new BigDecimal("0.25834"),
						new BigDecimal("1698"),
						new BigDecimal("0.25835"),
						new BigDecimal("674"),
						Instant.parse("2026-03-13T10:18:32.770Z")
		);
		Fees shortFees = new Fees(
						new BigDecimal("0.00036"),
						new BigDecimal("0.001"),
						new BigDecimal("0.00036"),
						new BigDecimal("0.001"),
						Instant.parse("2026-03-13T10:09:32.171Z")
		);
		FundingRate shortFunding = new FundingRate(
						new BigDecimal("-0.00467432"),
						Instant.parse("2026-03-13T11:00:00Z"),
						Instant.parse("2026-03-13T10:18:30.486Z")
		);
		MarkPrice shortMark = new MarkPrice(
						new BigDecimal("0.25856"),
						Instant.parse("2026-03-13T10:18:32.770Z")
		);
		ExchangeSnapshot shortExchange = new ExchangeSnapshot(shortBook, shortFunding, shortMark);

		ExchangeConstantData longConstantData = new ExchangeConstantData(new BigDecimal("0.1"), longFees, 4);
		ExchangeConstantData shortConstantData = new ExchangeConstantData(new BigDecimal("0.1"), shortFees, 1);

		ArbitrageData snapshot = new ArbitrageData(
						new ArbitrageSnapshot(longExchange, shortExchange),
						new ArbitrageConstantData(longConstantData, shortConstantData)
		);
		assertFalse(strategy().arbDataGoodEnough(snapshot));
	}
}
