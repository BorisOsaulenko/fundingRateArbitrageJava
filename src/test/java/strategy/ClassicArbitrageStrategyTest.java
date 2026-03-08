package strategy;

import com.boris.fundingarbitrage.strategy.ClassicInTradeStrategy;
import com.boris.fundingarbitrage.strategy.InTradeStrategy;

public class ClassicArbitrageStrategyTest extends ArbitrageStrategyTest {
	private final InTradeStrategy strat = new ClassicInTradeStrategy();

	@Override
	protected InTradeStrategy strategy() {
		return strat;
	}
}

