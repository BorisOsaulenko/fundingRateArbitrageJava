package execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.withdrawer.OptimalWithdrawerLogic;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OptimalWithdrawerLogicTest {
	private final OptimalWithdrawerLogic logic = new OptimalWithdrawerLogic();

	@Test
	void shouldGiveCorrectAnswer() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		var item1 = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						BigDecimal.valueOf(31),
						BigDecimal.ZERO,
						BigDecimal.ONE,
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		var item2 = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BITGET),
						BigDecimal.valueOf(11),
						BigDecimal.ZERO,
						BigDecimal.TWO,
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		var item3 = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BINANCE),
						BigDecimal.valueOf(32),
						BigDecimal.TWO,
						BigDecimal.TWO,
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		input.add(item1);
		input.add(item2);
		input.add(item3);
		OptimalWithdrawerLogic.InputParams
						params =
						new OptimalWithdrawerLogic.InputParams(BigDecimal.valueOf(20), BigDecimal.valueOf(40), input);

		var result = logic.getOptimalWdPath(params);
		var expected1 = new OptimalWithdrawerLogic.OutputItem(item3.ex(), BigDecimal.valueOf(30), BigDecimal.TWO, false);
		var expected2 = new OptimalWithdrawerLogic.OutputItem(item1.ex(), BigDecimal.valueOf(20), BigDecimal.ZERO, true);
		var expected3 = new OptimalWithdrawerLogic.OutputItem(item1.ex(), BigDecimal.valueOf(10), BigDecimal.ONE, false);

		assertEquals(BigDecimal.valueOf(3), BigDecimal.valueOf(result.size()));
		assertThat(result, hasItem(expected1));
		assertThat(result, hasItem(expected2));
		assertThat(result, hasItem(expected3));
	}

	@Test
	void singleBestExchangesForLongAndShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = BigDecimal.valueOf(40);
		BigDecimal topUpShort = BigDecimal.valueOf(50);

		// Single best for long
		OptimalWithdrawerLogic.InputItem bestLong = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						BigDecimal.valueOf(60),
						BigDecimal.valueOf(0.5),
						BigDecimal.ONE,
						BigDecimal.valueOf(15),
						BigDecimal.valueOf(15)
		);
		OptimalWithdrawerLogic.OutputItem
						expectedLong =
						new OptimalWithdrawerLogic.OutputItem(bestLong.ex(), BigDecimal.valueOf(40), BigDecimal.valueOf(0.5), true);
		input.add(bestLong);

		// Single best for short
		OptimalWithdrawerLogic.InputItem bestShort = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BITGET),
						BigDecimal.valueOf(70),
						BigDecimal.TWO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(15),
						BigDecimal.valueOf(15)
		);
		OptimalWithdrawerLogic.OutputItem
						expectedShort =
						new OptimalWithdrawerLogic.OutputItem(bestShort.ex(), BigDecimal.valueOf(50), BigDecimal.ZERO, false);
		input.add(bestShort);

		// Generate random other exchanges with less balance / more fees
		input.add(new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BYBIT),
						BigDecimal.valueOf(80),
						BigDecimal.valueOf(1.5),
						BigDecimal.ONE,
						BigDecimal.valueOf(15),
						BigDecimal.valueOf(15)
		));
		input.add(new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.GATE),
						BigDecimal.valueOf(90),
						BigDecimal.valueOf(0.7),
						BigDecimal.valueOf(0.3),
						BigDecimal.valueOf(15),
						BigDecimal.valueOf(15)
		));

		OptimalWithdrawerLogic.InputParams params = new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input);

		List<OptimalWithdrawerLogic.OutputItem> result = logic.getOptimalWdPath(params);
		assertEquals(BigDecimal.TWO, BigDecimal.valueOf(result.size()));
		assertThat(result, hasItem(expectedShort));
		assertThat(result, hasItem(expectedLong));
	}

	@Test
	void oneExchangeForLongTwoDifferentForShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = BigDecimal.valueOf(20);
		BigDecimal topUpShort = BigDecimal.valueOf(35);

		OptimalWithdrawerLogic.InputItem longOnlyBest = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						BigDecimal.valueOf(45),
						BigDecimal.ZERO,
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		OptimalWithdrawerLogic.InputItem shortPart1 = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BITGET),
						BigDecimal.valueOf(18),
						BigDecimal.valueOf(9),
						BigDecimal.ZERO,
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		OptimalWithdrawerLogic.InputItem shortPart2 = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BINANCE),
						BigDecimal.valueOf(30),
						BigDecimal.valueOf(8),
						BigDecimal.valueOf(0.2),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		OptimalWithdrawerLogic.InputItem expensiveNoise = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BYBIT),
						BigDecimal.valueOf(200),
						BigDecimal.valueOf(3),
						BigDecimal.valueOf(4),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		input.add(longOnlyBest);
		input.add(shortPart1);
		input.add(shortPart2);
		input.add(expensiveNoise);

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));

		assertEquals(BigDecimal.valueOf(3), BigDecimal.valueOf(result.size()));
		OptimalWithdrawerLogic.OutputItem longOutput = findOutput(result, longOnlyBest.ex(), true);
		OptimalWithdrawerLogic.OutputItem shortFromPart1 = findOutput(result, shortPart1.ex(), false);
		OptimalWithdrawerLogic.OutputItem shortFromPart2 = findOutput(result, shortPart2.ex(), false);

		assertNotNull(longOutput);
		assertNotNull(shortFromPart1);
		assertNotNull(shortFromPart2);
		assertEquals(0, BigDecimal.valueOf(20).compareTo(longOutput.amount()));
		assertEquals(0, BigDecimal.valueOf(35).compareTo(shortFromPart1.amount().add(shortFromPart2.amount())));
		assertEquals(BigDecimal.ZERO, shortFromPart1.fee());
		assertEquals(BigDecimal.valueOf(0.2), shortFromPart2.fee());
	}

	@Test
	void singleExchangeSplitToLongAndShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = BigDecimal.valueOf(30);
		BigDecimal topUpShort = BigDecimal.valueOf(20);

		OptimalWithdrawerLogic.InputItem splitExchange = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						BigDecimal.valueOf(70),
						BigDecimal.valueOf(0.1),
						BigDecimal.valueOf(0.1),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		OptimalWithdrawerLogic.InputItem expensiveAlt = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BITGET),
						BigDecimal.valueOf(200),
						BigDecimal.valueOf(4),
						BigDecimal.valueOf(4),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		input.add(splitExchange);
		input.add(expensiveAlt);

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));

		assertEquals(BigDecimal.TWO, BigDecimal.valueOf(result.size()));
		assertThat(
						result,
						hasItem(new OptimalWithdrawerLogic.OutputItem(
										splitExchange.ex(),
										BigDecimal.valueOf(30),
										BigDecimal.valueOf(0.1),
										true
						))
		);
		assertThat(
						result,
						hasItem(new OptimalWithdrawerLogic.OutputItem(
										splitExchange.ex(),
										BigDecimal.valueOf(20),
										BigDecimal.valueOf(0.1),
										false
						))
		);
	}

	@Test
	void splitExchangePlusExtraShortExchangeForRemainder() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = BigDecimal.valueOf(25);
		BigDecimal topUpShort = BigDecimal.valueOf(25);

		OptimalWithdrawerLogic.InputItem splitExchange = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						BigDecimal.valueOf(35),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		OptimalWithdrawerLogic.InputItem extraShort = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BINANCE),
						BigDecimal.valueOf(20),
						BigDecimal.valueOf(8),
						BigDecimal.valueOf(0.1),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		OptimalWithdrawerLogic.InputItem expensiveLongNoise = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.GATE),
						BigDecimal.valueOf(100),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(6),
						BigDecimal.valueOf(5),
						BigDecimal.valueOf(5)
		);
		input.add(splitExchange);
		input.add(extraShort);
		input.add(expensiveLongNoise);

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));

		OptimalWithdrawerLogic.OutputItem splitLong = findOutput(result, splitExchange.ex(), true);
		OptimalWithdrawerLogic.OutputItem splitShort = findOutput(result, splitExchange.ex(), false);
		OptimalWithdrawerLogic.OutputItem shortRemainder = findOutput(result, extraShort.ex(), false);

		assertNotNull(splitLong);
		assertNotNull(splitShort);
		assertNotNull(shortRemainder);

		assertEquals(BigDecimal.valueOf(25), splitLong.amount());
		assertEquals(BigDecimal.valueOf(5.1), splitShort.amount());
		assertEquals(BigDecimal.valueOf(19.9), shortRemainder.amount());
		assertEquals(BigDecimal.ZERO, splitLong.fee());
		assertEquals(BigDecimal.ZERO, splitShort.fee());
		assertEquals(BigDecimal.valueOf(0.1), shortRemainder.fee());
	}

	private OptimalWithdrawerLogic.OutputItem findOutput(
					List<OptimalWithdrawerLogic.OutputItem> outputs,
					BaseExchange exchange,
					boolean toLong
	) {
		for (OptimalWithdrawerLogic.OutputItem output : outputs) {
			if (output.ex() == exchange && output.toLong() == toLong) {
				return output;
			}
		}
		return null;
	}
}
