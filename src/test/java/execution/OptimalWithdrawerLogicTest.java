package execution;

import com.boris.fundingarbitrage.execution.withdrawer.OptimalWithdrawerLogic;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import com.boris.fundingarbitrage.util.logger.Logger;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
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

	private TypeSafeMatcher<OptimalWithdrawerLogic.OutputItem> matchesOutputItem(OptimalWithdrawerLogic.OutputItem expected) {
		return new TypeSafeMatcher<>() {
			@Override
			protected boolean matchesSafely(OptimalWithdrawerLogic.OutputItem actual) {
				return actual.exName() == expected.exName() &&
							 actual.amount().compareTo(expected.amount()) == 0 &&
							 actual.fee().compareTo(expected.fee()) == 0 &&
							 actual.toLong() == expected.toLong();
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("OutputItem matching ").appendValue(expected);
			}
		};
	}

	private void assertEqualsDecimal(BigDecimal a, BigDecimal b) {
		assertEquals(0, a.compareTo(b), "Expected " + a + " to be equal to " + b);
	}


	@Test
	void shouldGiveCorrectAnswer() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		var item1 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.OKX,
						new BigDecimal("31"),
						BigDecimal.ZERO,
						BigDecimal.ONE,
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		var item2 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BITGET,
						new BigDecimal("11"),
						BigDecimal.ZERO,
						BigDecimal.TWO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		var item3 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BINANCE,
						new BigDecimal("32"),
						BigDecimal.TWO,
						BigDecimal.TWO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		input.add(item1);
		input.add(item2);
		input.add(item3);
		OptimalWithdrawerLogic.InputParams params = new OptimalWithdrawerLogic.InputParams(
						new BigDecimal("20"),
						new BigDecimal("40"),
						input
		);

		var result = logic.getOptimalWdPath(params);
		var expected1 = new OptimalWithdrawerLogic.OutputItem(item3.exName(), new BigDecimal("30"), BigDecimal.TWO, false);
		var expected2 = new OptimalWithdrawerLogic.OutputItem(item1.exName(), new BigDecimal("20"), BigDecimal.ZERO, true);
		var expected3 = new OptimalWithdrawerLogic.OutputItem(item1.exName(), new BigDecimal("10"), BigDecimal.ONE, false);

		assertEquals(new BigDecimal("3"), new BigDecimal(result.size()));
		assertThat(result, hasItem(matchesOutputItem(expected1)));
		assertThat(result, hasItem(matchesOutputItem(expected2)));
		assertThat(result, hasItem(matchesOutputItem(expected3)));
	}

	@Test
	void singleBestExchangesForLongAndShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = new BigDecimal("40");
		BigDecimal topUpShort = new BigDecimal("50");

		// Single best for long
		OptimalWithdrawerLogic.InputItem bestLong = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.OKX,
						new BigDecimal("60"),
						new BigDecimal("0.5"),
						BigDecimal.ONE,
						new BigDecimal("15"),
						new BigDecimal("15"),
						18,
						18
		);
		OptimalWithdrawerLogic.OutputItem expectedLong = new OptimalWithdrawerLogic.OutputItem(
						bestLong.exName(),
						new BigDecimal("40"),
						new BigDecimal("0.5"),
						true
		);
		input.add(bestLong);

		// Single best for short
		OptimalWithdrawerLogic.InputItem bestShort = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BITGET,
						new BigDecimal("70"),
						BigDecimal.TWO,
						BigDecimal.ZERO,
						new BigDecimal("15"),
						new BigDecimal("15"),
						18,
						18

		);
		OptimalWithdrawerLogic.OutputItem expectedShort = new OptimalWithdrawerLogic.OutputItem(
						bestShort.exName(),
						new BigDecimal("50"),
						BigDecimal.ZERO,
						false
		);
		input.add(bestShort);

		// Generate random other exchanges with less balance / more fees
		input.add(new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BYBIT,
						new BigDecimal("80"),
						new BigDecimal("1.5"),
						BigDecimal.ONE,
						new BigDecimal("15"),
						new BigDecimal("15"),
						18,
						18
		));
		input.add(new OptimalWithdrawerLogic.InputItem(
						ExchangeName.GATE,
						new BigDecimal("90"),
						new BigDecimal("0.7"),
						new BigDecimal("0.3"),
						new BigDecimal("15"),
						new BigDecimal("15"),
						18,
						18
		));

		OptimalWithdrawerLogic.InputParams params = new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input);

		List<OptimalWithdrawerLogic.OutputItem> result = logic.getOptimalWdPath(params);
		assertEquals(BigDecimal.TWO, new BigDecimal(result.size()));
		assertThat(result, hasItem(matchesOutputItem(expectedShort)));
		assertThat(result, hasItem(matchesOutputItem(expectedLong)));
	}

	@Test
	void oneExchangeForLongTwoDifferentForShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = new BigDecimal("20");
		BigDecimal topUpShort = new BigDecimal("35");

		OptimalWithdrawerLogic.InputItem longOnlyBest = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.OKX,
						new BigDecimal("45"),
						BigDecimal.ZERO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem shortPart1 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BITGET,
						new BigDecimal("18"),
						new BigDecimal("9"),
						BigDecimal.ZERO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem shortPart2 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BINANCE,
						new BigDecimal("30"),
						new BigDecimal("8"),
						new BigDecimal("0.2"),
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem expensiveNoise = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BYBIT,
						new BigDecimal("200"),
						new BigDecimal("3"),
						new BigDecimal("4"),
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		input.add(longOnlyBest);
		input.add(shortPart1);
		input.add(shortPart2);
		input.add(expensiveNoise);

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));
		Logger.log(result.toString());
		assertEquals(3, result.size());
		OptimalWithdrawerLogic.OutputItem longOutput = findOutput(result, longOnlyBest.exName(), true);
		OptimalWithdrawerLogic.OutputItem shortFromPart1 = findOutput(result, shortPart1.exName(), false);
		OptimalWithdrawerLogic.OutputItem shortFromPart2 = findOutput(result, shortPart2.exName(), false);

		assertNotNull(longOutput);
		assertNotNull(shortFromPart1);
		assertNotNull(shortFromPart2);
		assertEquals(0, new BigDecimal("20").compareTo(longOutput.amount()));
		assertEquals(0, new BigDecimal("35").compareTo(shortFromPart1.amount().add(shortFromPart2.amount())));
		assertEquals(BigDecimal.ZERO, shortFromPart1.fee());
		assertEquals(new BigDecimal("0.2"), shortFromPart2.fee());
	}

	@Test
	void singleExchangeSplitToLongAndShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = new BigDecimal("30");
		BigDecimal topUpShort = new BigDecimal("20");

		OptimalWithdrawerLogic.InputItem splitExchange = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.OKX,
						new BigDecimal("70"),
						new BigDecimal("0.1"),
						new BigDecimal("0.1"),
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem expensiveAlt = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BITGET,
						new BigDecimal("200"),
						new BigDecimal("4"),
						new BigDecimal("4"),
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		input.add(splitExchange);
		input.add(expensiveAlt);

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));

		assertEquals(BigDecimal.TWO, new BigDecimal(result.size()));
		assertThat(
						result,
						hasItem(matchesOutputItem(new OptimalWithdrawerLogic.OutputItem(
										splitExchange.exName(),
										new BigDecimal("30"),
										new BigDecimal("0.1"),
										true
						)))
		);
		assertThat(
						result,
						hasItem(matchesOutputItem(new OptimalWithdrawerLogic.OutputItem(
										splitExchange.exName(),
										new BigDecimal("20"),
										new BigDecimal("0.1"),
										false
						)))
		);
	}

	@Test
	void splitExchangePlusExtraShortExchangeForRemainder() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		BigDecimal topUpLong = new BigDecimal("25");
		BigDecimal topUpShort = new BigDecimal("25");

		OptimalWithdrawerLogic.InputItem splitExchange = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.OKX,
						new BigDecimal("35"),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem extraShort = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BINANCE,
						new BigDecimal("20"),
						new BigDecimal("8"),
						new BigDecimal("0.1"),
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		OptimalWithdrawerLogic.InputItem expensiveLongNoise = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.GATE,
						new BigDecimal("100"),
						new BigDecimal("5"),
						new BigDecimal("6"),
						new BigDecimal("5"),
						new BigDecimal("5"),
						18,
						18
		);
		input.add(splitExchange);
		input.add(extraShort);
		input.add(expensiveLongNoise);

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));

		OptimalWithdrawerLogic.OutputItem splitLong = findOutput(result, splitExchange.exName(), true);
		OptimalWithdrawerLogic.OutputItem splitShort = findOutput(result, splitExchange.exName(), false);
		OptimalWithdrawerLogic.OutputItem shortRemainder = findOutput(result, extraShort.exName(), false);

		assertNotNull(splitLong);
		assertNotNull(splitShort);
		assertNotNull(shortRemainder);

		assertEqualsDecimal(new BigDecimal("25"), splitLong.amount());
		assertEqualsDecimal(new BigDecimal("5.1"), splitShort.amount());
		assertEqualsDecimal(new BigDecimal("19.9"), shortRemainder.amount());
		assertEqualsDecimal(BigDecimal.ZERO, splitLong.fee());
		assertEqualsDecimal(BigDecimal.ZERO, splitShort.fee());
		assertEqualsDecimal(new BigDecimal("0.1"), shortRemainder.fee());
	}

	@Test
	void shouldAccountForPrecision1() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		var item1 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.OKX,
						new BigDecimal("30"),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						1,
						1
		);
		var item2 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BITGET,
						new BigDecimal("11"),
						BigDecimal.ONE,
						BigDecimal.ONE,
						new BigDecimal("5"),
						new BigDecimal("5"),
						1,
						1
		);
		var item3 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BINANCE,
						new BigDecimal("32"),
						BigDecimal.TWO,
						BigDecimal.TWO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						1,
						1
		);
		input.add(item1);
		input.add(item2);
		input.add(item3);
		OptimalWithdrawerLogic.InputParams params = new OptimalWithdrawerLogic.InputParams(
						new BigDecimal("20"),
						new BigDecimal("39.99"),
						input
		);

		var result = logic.getOptimalWdPath(params);
		var expected1 = new OptimalWithdrawerLogic.OutputItem(item3.exName(), new BigDecimal("30"), BigDecimal.TWO, false);
		var expected2 = new OptimalWithdrawerLogic.OutputItem(item1.exName(), new BigDecimal("20"), BigDecimal.ZERO, true);
		var expected3 = new OptimalWithdrawerLogic.OutputItem(item1.exName(), new BigDecimal("10"), BigDecimal.ZERO, false);

		assertEquals(new BigDecimal("3"), new BigDecimal(result.size()));
		assertThat(result, hasItem(matchesOutputItem(expected1)));
		assertThat(result, hasItem(matchesOutputItem(expected2)));
		assertThat(result, hasItem(matchesOutputItem(expected3)));
	}

	@Test
	void shouldAccountForPrecision2() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		var item1 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.OKX,
						new BigDecimal("30"),
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						1,
						1
		);
		var item2 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BITGET,
						new BigDecimal("11"),
						BigDecimal.ONE,
						BigDecimal.ONE,
						new BigDecimal("5"),
						new BigDecimal("5"),
						1,
						1
		);
		var item3 = new OptimalWithdrawerLogic.InputItem(
						ExchangeName.BINANCE,
						new BigDecimal("32"),
						BigDecimal.TWO,
						BigDecimal.TWO,
						new BigDecimal("5"),
						new BigDecimal("5"),
						1,
						1
		);
		input.add(item1);
		input.add(item2);
		input.add(item3);
		OptimalWithdrawerLogic.InputParams params = new OptimalWithdrawerLogic.InputParams(
						new BigDecimal("20"),
						new BigDecimal("40.01"),
						input
		);

		var result = logic.getOptimalWdPath(params);
		var expected1 = new OptimalWithdrawerLogic.OutputItem(item3.exName(), new BigDecimal("30"), BigDecimal.TWO, false);
		var expected2 = new OptimalWithdrawerLogic.OutputItem(item1.exName(), new BigDecimal("20"), BigDecimal.ZERO, true);
		var expected3 = new OptimalWithdrawerLogic.OutputItem(item1.exName(), new BigDecimal("5"), BigDecimal.ZERO, false);
		var expected4 = new OptimalWithdrawerLogic.OutputItem(item2.exName(), new BigDecimal("5.1"), BigDecimal.ONE, false);

		assertEquals(new BigDecimal("4"), new BigDecimal(result.size()));
		assertThat(result, hasItem(matchesOutputItem(expected1)));
		assertThat(result, hasItem(matchesOutputItem(expected2)));
		assertThat(result, hasItem(matchesOutputItem(expected3)));
		assertThat(result, hasItem(matchesOutputItem(expected4)));
	}

	private OptimalWithdrawerLogic.OutputItem findOutput(
					List<OptimalWithdrawerLogic.OutputItem> outputs,
					ExchangeName exchange,
					boolean toLong
	) {
		for (OptimalWithdrawerLogic.OutputItem output : outputs) {
			if (output.exName() == exchange && output.toLong() == toLong) {
				return output;
			}
		}
		return null;
	}
}
