package execution;

import com.boris.fundingarbitrage.exchange.BaseExchange;
import com.boris.fundingarbitrage.execution.OptimalWithdrawerLogic;
import com.boris.fundingarbitrage.model.exchange.ExchangeName;
import org.junit.jupiter.api.Test;

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
		var item1 = new OptimalWithdrawerLogic.InputItem(new TestExchange(ExchangeName.OKX), 31, 0.0, 1.0, 5.0, 5.0);
		var item2 = new OptimalWithdrawerLogic.InputItem(new TestExchange(ExchangeName.BITGET), 11, 1.0, 2.0, 5.0, 5.0);
		var item3 = new OptimalWithdrawerLogic.InputItem(new TestExchange(ExchangeName.BINANCE), 32, 2.0, 2.0, 5.0, 5.0);
		input.add(item1);
		input.add(item2);
		input.add(item3);
		OptimalWithdrawerLogic.InputParams params = new OptimalWithdrawerLogic.InputParams(20, 40, input);

		var result = logic.getOptimalWdPath(params);
		var expected1 = new OptimalWithdrawerLogic.OutputItem(item3.ex(), 30, 2.0, false);
		var expected2 = new OptimalWithdrawerLogic.OutputItem(item1.ex(), 20, 0.0, true);
		var expected3 = new OptimalWithdrawerLogic.OutputItem(item1.ex(), 10, 1.0, false);

		assertEquals(3, result.size());
		assertThat(result, hasItem(expected1));
		assertThat(result, hasItem(expected2));
		assertThat(result, hasItem(expected3));
	}

	@Test
	void singleBestExchangesForLongAndShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		double topUpLong = 40;
		double topUpShort = 50;

		// Single best for long
		OptimalWithdrawerLogic.InputItem bestLong = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						60,
						0.5,
						1.0,
						15.0,
						15.0
		);
		OptimalWithdrawerLogic.OutputItem expectedLong = new OptimalWithdrawerLogic.OutputItem(
						bestLong.ex(),
						40,
						0.5,
						true
		);
		input.add(bestLong);

		// Single best for short
		OptimalWithdrawerLogic.InputItem bestShort = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BITGET),
						70,
						2.0,
						0.0,
						15.0,
						15.0
		);
		OptimalWithdrawerLogic.OutputItem expectedShort = new OptimalWithdrawerLogic.OutputItem(
						bestShort.ex(),
						50,
						0,
						false
		);
		input.add(bestShort);

		// Generate random other exchanges with less balance / more fees
		input.add(new OptimalWithdrawerLogic.InputItem(new TestExchange(ExchangeName.BYBIT), 80, 1.5, 1.0, 15.0, 15.0));
		input.add(new OptimalWithdrawerLogic.InputItem(new TestExchange(ExchangeName.GATE), 90, 0.7, 0.3, 15.0, 15.0));

		OptimalWithdrawerLogic.InputParams params = new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input);

		List<OptimalWithdrawerLogic.OutputItem> result = logic.getOptimalWdPath(params);
		assertEquals(2, result.size());
		assertThat(result, hasItem(expectedShort));
		assertThat(result, hasItem(expectedLong));
	}

	@Test
	void oneExchangeForLongTwoDifferentForShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		double topUpLong = 20;
		double topUpShort = 35;

		OptimalWithdrawerLogic.InputItem longOnlyBest = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						45,
						0.0,
						5.0,
						5.0,
						5.0
		);
		OptimalWithdrawerLogic.InputItem shortPart1 = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BITGET),
						18,
						9.0,
						0.0,
						5.0,
						5.0
		);
		OptimalWithdrawerLogic.InputItem shortPart2 = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BINANCE),
						30,
						8.0,
						0.2,
						5.0,
						5.0
		);
		OptimalWithdrawerLogic.InputItem expensiveNoise = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BYBIT),
						200,
						3.0,
						4.0,
						5.0,
						5.0
		);
		input.add(longOnlyBest);
		input.add(shortPart1);
		input.add(shortPart2);
		input.add(expensiveNoise);

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));

		assertEquals(3, result.size());
		OptimalWithdrawerLogic.OutputItem longOutput = findOutput(result, longOnlyBest.ex(), true);
		OptimalWithdrawerLogic.OutputItem shortFromPart1 = findOutput(result, shortPart1.ex(), false);
		OptimalWithdrawerLogic.OutputItem shortFromPart2 = findOutput(result, shortPart2.ex(), false);

		assertNotNull(longOutput);
		assertNotNull(shortFromPart1);
		assertNotNull(shortFromPart2);
		assertEquals(20, longOutput.amount());
		assertEquals(35, shortFromPart1.amount() + shortFromPart2.amount());
		assertEquals(0.0, shortFromPart1.fee());
		assertEquals(0.2, shortFromPart2.fee());
	}

	@Test
	void singleExchangeSplitToLongAndShort() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		double topUpLong = 30;
		double topUpShort = 20;

		OptimalWithdrawerLogic.InputItem splitExchange = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						70,
						0.1,
						0.1,
						5.0,
						5.0
		);
		OptimalWithdrawerLogic.InputItem expensiveAlt = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BITGET),
						200,
						4.0,
						4.0,
						5.0,
						5.0
		);
		input.add(splitExchange);
		input.add(expensiveAlt);

		var result = logic.getOptimalWdPath(new OptimalWithdrawerLogic.InputParams(topUpLong, topUpShort, input));

		assertEquals(2, result.size());
		assertThat(result, hasItem(new OptimalWithdrawerLogic.OutputItem(splitExchange.ex(), 30, 0.1, true)));
		assertThat(result, hasItem(new OptimalWithdrawerLogic.OutputItem(splitExchange.ex(), 20, 0.1, false)));
	}

	@Test
	void splitExchangePlusExtraShortExchangeForRemainder() {
		List<OptimalWithdrawerLogic.InputItem> input = new ArrayList<>();
		double topUpLong = 25;
		double topUpShort = 25;

		OptimalWithdrawerLogic.InputItem splitExchange = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.OKX),
						35,
						0.0,
						0.0,
						5.0,
						5.0
		);
		OptimalWithdrawerLogic.InputItem extraShort = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(ExchangeName.BINANCE),
						20,
						8.0,
						0.1,
						5.0,
						5.0
		);
		OptimalWithdrawerLogic.InputItem expensiveLongNoise = new OptimalWithdrawerLogic.InputItem(
						new TestExchange(
						ExchangeName.GATE), 100, 5.0, 6.0, 5.0, 5.0
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

		assertEquals(25, splitLong.amount());
		assertEquals(5.1, splitShort.amount());
		assertEquals(19.9, shortRemainder.amount());
		assertEquals(0.0, splitLong.fee());
		assertEquals(0.0, splitShort.fee());
		assertEquals(0.1, shortRemainder.fee());
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
